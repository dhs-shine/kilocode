import { existsSync } from "node:fs"
import { Effect } from "effect"
import type { Backend, Launch, PreparedLaunch, Support } from "./backend"
import type { PathRule, Profile } from "./profile"
import { base } from "./seatbelt-base"

const executable = "/usr/bin/sandbox-exec"

interface Param {
  readonly key: string
  readonly value: string
}

function filter(rule: PathRule, key: string) {
  if (rule.kind === "literal") return `(literal (param "${key}"))`
  return `(require-any (literal (param "${key}")) (subpath (param "${key}")))`
}

function escape(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

function quote(value: string) {
  return `'${value.replaceAll("'", `'\\''`)}'`
}

function exclude(rule: PathRule, key: string) {
  if (rule.kind === "literal") return [`(require-not (literal (param "${key}")))`]
  return [`(require-not (literal (param "${key}")))`, `(require-not (subpath (param "${key}")))`]
}

function policy(profile: Profile) {
  const params: Array<Param> = []
  const allow = profile.filesystem.allowWrite.map((rule, index) => {
    const key = `ALLOW_WRITE_${index}`
    params.push({ key, value: rule.path })
    return filter(rule, key)
  })
  const rules = profile.filesystem.writeRules.map((item, index) => {
    const key = `WRITE_RULE_${index}`
    params.push({ key, value: item.rule.path })
    return { ...item, key }
  })
  const external = rules.flatMap((item, index) => {
    if (item.action !== "allow") return []
    const later = rules.slice(index + 1).flatMap((rule) => exclude(rule.rule, rule.key))
    return [`(require-all ${filter(item.rule, item.key)} ${later.join(" ")})`]
  })
  const deny = profile.filesystem.denyWrite.flatMap((rule, index) => {
    const key = `DENY_WRITE_${index}`
    params.push({ key, value: rule.path })
    return exclude(rule, key)
  })
  const names = profile.filesystem.denyNames.map((name) => `(require-not (regex #"(^|/)${escape(name)}(/|$)"))`)
  const sources = [...allow, ...external]
  const write =
    sources.length === 0
      ? ""
      : `(allow file-write*\n  (require-all\n    (require-any ${sources.join(" ")})\n    ${[...deny, ...names].join("\n    ")}\n  )\n)`
  return {
    value: [base, "; reads are not confined by the file-level sandbox\n(allow file-read*)", write].join("\n"),
    params,
  }
}

export function generate(profile: Profile, launch: Launch, support: Support): PreparedLaunch {
  const generated = policy(profile)
  const args = ["-p", generated.value, ...generated.params.map((param) => `-D${param.key}=${param.value}`)]
  const command = launch.shell ? (typeof launch.shell === "string" ? launch.shell : "/bin/sh") : launch.command
  const commandArgs = launch.shell ? ["-c", [launch.command, ...launch.args.map(quote)].join(" ")] : launch.args
  args.push("--", command, ...commandArgs)
  return {
    ...launch,
    command: executable,
    args,
    sandboxed: true,
    support,
  }
}

const available: Support = existsSync(executable)
  ? { available: true }
  : { available: false, reason: `${executable} is not available` }

export const seatbelt: Backend = {
  support: available,
  prepare: (profile, launch) =>
    available.available
      ? Effect.succeed(generate(profile, launch, available))
      : Effect.succeed({ ...launch, sandboxed: false, support: available }),
}
