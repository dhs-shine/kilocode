import { Effect } from "effect"
import { isAbsolute, resolve as pathResolve } from "node:path"
import { Global } from "@opencode-ai/core/global"
import { run as runSandbox, grantWrite, type PathRule, type Profile } from "@kilocode/sandbox"
import { Config } from "@/config/config"
import { InstanceState } from "@/effect/instance-state"
import { Permission } from "@/permission"
import type { InstanceContext } from "@/project/instance-context"

function root(path: string): PathRule {
  return { path, kind: "subtree" }
}

function pattern(value: string, ctx: InstanceContext): PathRule | undefined {
  const input = value.trim()
  if (input === "*") return root(pathResolve("/"))
  const match = input.match(/^(.*?)[\\/]\*+$/)
  const path = match?.[1] ?? input
  if (!path || path.includes("*") || path.includes("?")) return undefined
  return {
    path: isAbsolute(path) ? path : pathResolve(ctx.directory, path),
    kind: match ? "subtree" : "literal",
  }
}

function rules(ctx: InstanceContext, cfg: Config.Info) {
  return Permission.fromConfig(cfg.permission ?? {}).flatMap((rule) => {
    if (rule.permission !== "external_directory") return []
    const item = pattern(rule.pattern, ctx)
    return item ? [{ action: rule.action, rule: item }] : []
  })
}

export function profile(ctx: InstanceContext, cfg: Config.Info): Profile {
  const writable = [
    ...(ctx.worktree === "/" ? [] : [ctx.worktree]),
    ctx.directory,
    ...(ctx.project.sandboxes ?? []),
    Global.Path.data,
    Global.Path.cache,
    Global.Path.config,
    Global.Path.state,
    Global.Path.tmp,
    Global.Path.bin,
    Global.Path.log,
    Global.Path.repos,
  ].map(root)
  return {
    filesystem: {
      allowWrite: writable,
      denyWrite: [],
      writeRules: rules(ctx, cfg),
      denyNames: [".git"],
      temporaryDirectory: Global.Path.tmp,
    },
    network: {
      mode: "allow",
      allowedHosts: [],
    },
    environment: {
      deny: [],
      set: {
        TMPDIR: Global.Path.tmp,
        TMP: Global.Path.tmp,
        TEMP: Global.Path.tmp,
      },
    },
  }
}

export function execute<A, E, R>(effect: Effect.Effect<A, E, R>) {
  return Effect.gen(function* () {
    const config = yield* Config.Service
    const cfg = yield* config.get()
    if (!cfg.experimental?.sandbox) return yield* effect
    return yield* runSandbox(profile(yield* InstanceState.context, cfg), effect)
  })
}

export function approved(input: {
  permission: string
  patterns: readonly string[]
  metadata: Record<string, unknown>
}) {
  return Effect.gen(function* () {
    if (input.permission !== "external_directory" || input.metadata.access === "read") return
    const ctx = yield* InstanceState.context
    for (const value of input.patterns) {
      const rule = pattern(value, ctx)
      if (rule) yield* grantWrite(rule.path, rule.kind)
    }
  })
}
