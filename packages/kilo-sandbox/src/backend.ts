import { Effect, PlatformError, Scope } from "effect"
import { ChildProcess } from "effect/unstable/process"
import { current } from "./context"
import type { Profile } from "./profile"
import { seatbelt } from "./seatbelt"

export interface Launch {
  readonly command: string
  readonly args: ReadonlyArray<string>
  readonly cwd?: string | undefined
  readonly environment?: Readonly<Record<string, string | undefined>> | undefined
  readonly shell?: boolean | string | undefined
}

export interface Support {
  readonly available: boolean
  readonly reason?: string | undefined
}

export interface PreparedLaunch extends Launch {
  readonly sandboxed: boolean
  readonly support: Support
}

export interface Backend {
  readonly support: Support
  readonly prepare: (profile: Profile, launch: Launch) => Effect.Effect<PreparedLaunch, never, Scope.Scope>
}

function unavailable(reason: string): Backend {
  const support: Support = { available: false, reason }
  return {
    support,
    prepare: (_profile, launch) => Effect.succeed({ ...launch, sandboxed: false, support }),
  }
}

function select(): Backend {
  switch (process.platform) {
    case "darwin":
      return seatbelt
    case "linux":
      return unavailable("The Linux sandbox backend is not available")
    case "win32":
      return unavailable("The Windows sandbox backend is not available")
    default:
      return unavailable("No sandbox backend is available for this operating system")
  }
}

const backend = select()

export const support: Effect.Effect<Support> = Effect.succeed(backend.support)

function environment(profile: Profile, launch: Launch) {
  const source = { ...launch.environment, ...profile.environment.set }
  const denied = new Set(profile.environment.deny)
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(source)) {
    if (value !== undefined && !denied.has(key)) result[key] = value
  }
  return result
}

function acquire(launch: Launch): Effect.Effect<PreparedLaunch, never, Scope.Scope> {
  return Effect.gen(function* () {
    const profile = yield* current
    if (!profile) return { ...launch, sandboxed: false, support: backend.support }
    return yield* backend.prepare(profile, { ...launch, environment: environment(profile, launch) })
  })
}

export function prepare(launch: Launch): Effect.Effect<PreparedLaunch, never, Scope.Scope> {
  return acquire(launch)
}

export function prepareCommand(
  command: ChildProcess.StandardCommand,
  cwd: string | undefined,
  env: Readonly<Record<string, string | undefined>> | undefined,
) {
  return Effect.gen(function* () {
    const profile = yield* current
    if (!profile) return command
    const launch = yield* prepare({
      command: command.command,
      args: command.args,
      cwd,
      environment: env,
      shell: command.options.shell,
    })
    if (!launch.sandboxed) {
      return yield* Effect.fail(
        PlatformError.systemError({
          _tag: "PermissionDenied",
          module: "Sandbox",
          method: "prepareCommand",
          pathOrDescriptor: command.command,
          description: launch.support.reason ?? "The process sandbox backend is unavailable",
        }),
      )
    }
    return ChildProcess.make(launch.command, launch.args, {
      ...command.options,
      cwd: launch.cwd,
      env: launch.environment,
      extendEnv: false,
      shell: false,
    })
  })
}
