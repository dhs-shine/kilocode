import { Effect } from "effect"
import { Config } from "@/config/config"
import { InstanceState } from "@/effect/instance-state"
import { Filesystem } from "@/util/filesystem"
import type { InstanceContext } from "@/project/instance-context"
import { available } from "./seatbelt"
import { resolve, withExternalDirs, type Scope } from "./scope"

function longestExistingAncestor(p: string): string {
  let dir = p
  let rest = ""
  const fs = require("fs")
  while (dir && dir !== "/" && !fs.existsSync(dir)) {
    const idx = dir.lastIndexOf("/")
    rest = `${dir.slice(idx)}${rest}`
    dir = dir.slice(0, idx)
  }
  if (!dir) return Filesystem.resolve(p)
  return Filesystem.resolve(dir) + rest
}

function isWritable(scope: Scope, filepath: string): boolean {
  const target = longestExistingAncestor(filepath)

  for (const root of scope.writableRoots) {
    if (!isUnder(target, root.path)) continue

    for (const sub of root.readonlySubpaths) {
      if (isUnder(target, sub) || target === sub) return false
    }
    return true
  }
  return false
}

function isUnder(child: string, parent: string): boolean {
  if (child === parent) return true
  if (parent === "/") return true
  return child.startsWith(`${parent}/`)
}

function externalDirAllows(cfg: Config.Info): string[] {
  const rule = cfg.permission?.external_directory
  if (!rule) return []
  if (typeof rule === "string") {
    return rule === "allow" ? ["*"] : []
  }
  const patterns: string[] = []
  for (const [pattern, action] of Object.entries(rule)) {
    if (action === "allow") patterns.push(pattern)
  }
  return patterns
}

export function assertWritable(
  config: Config.Interface,
  filepath: string,
): Effect.Effect<void> {
  return Effect.gen(function* () {
    const cfg = yield* config.get()
    if (!cfg.experimental?.sandbox) return
    if (process.platform !== "darwin" || !available()) return

    const ctx = yield* InstanceState.context
    const scope = enrichedScope(ctx, cfg)

    if (!isWritable(scope, filepath)) {
      throw new Error(
        `Sandbox blocked write to ${filepath}: outside the allowed project and Kilo directories. ` +
          `To allow this directory, approve it as an external directory or disable the sandbox.`,
      )
    }
  })
}

export function enrichedScope(ctx: InstanceContext, cfg: Config.Info): Scope {
  return withExternalDirs(resolve(ctx), externalDirAllows(cfg))
}
