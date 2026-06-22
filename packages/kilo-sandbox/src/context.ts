import { Context, Effect, PlatformError, Ref } from "effect"
import { canonicalize, canonicalizeEntry, matches, normalize } from "./path"
import type { Profile } from "./profile"

export const CurrentProfile = Context.Reference<Ref.Ref<Profile> | undefined>("@kilocode/sandbox/CurrentProfile", {
  defaultValue: () => undefined,
})

export const current: Effect.Effect<Profile | undefined> = Effect.gen(function* () {
  const ref = yield* CurrentProfile
  return ref ? yield* Ref.get(ref) : undefined
})

export const enabled: Effect.Effect<boolean> = Effect.map(current, (profile) => profile !== undefined)

export function run<A, E, R>(
  profile: Profile,
  effect: Effect.Effect<A, E, R>,
): Effect.Effect<A, E | PlatformError.PlatformError, R> {
  return Effect.gen(function* () {
    const value = yield* normalize(profile)
    const ref = yield* Ref.make(value)
    return yield* effect.pipe(Effect.provideService(CurrentProfile, ref))
  })
}

export function grantWrite(
  path: string,
  kind: "literal" | "subtree" = "literal",
): Effect.Effect<void, PlatformError.PlatformError> {
  return Effect.gen(function* () {
    const ref = yield* CurrentProfile
    if (!ref) return
    const target = yield* canonicalize(path)
    yield* Ref.update(ref, (profile) => {
      const last = profile.filesystem.writeRules.at(-1)
      if (last?.action === "allow" && last.rule.kind === kind && last.rule.path === target) return profile
      return {
        ...profile,
        filesystem: {
          ...profile.filesystem,
          writeRules: [...profile.filesystem.writeRules, { rule: { path: target, kind }, action: "allow" as const }],
        },
      }
    })
  })
}

function denied(path: string, method: string) {
  return PlatformError.systemError({
    _tag: "PermissionDenied",
    module: "FileSystem",
    method,
    pathOrDescriptor: path,
    description: "Sandbox denied write access",
  })
}

function assertTarget(
  path: string,
  method: string,
  resolve: (path: string) => Effect.Effect<string, PlatformError.PlatformError>,
): Effect.Effect<void, PlatformError.PlatformError> {
  return Effect.gen(function* () {
    const profile = yield* current
    if (!profile) return
    const target = yield* resolve(path)
    const names =
      process.platform === "win32"
        ? profile.filesystem.denyNames.map((name) => name.toLowerCase())
        : profile.filesystem.denyNames
    const parts = target.split(/[\\/]/).map((part) => (process.platform === "win32" ? part.toLowerCase() : part))
    if (
      profile.filesystem.denyWrite.some((rule) => matches(rule, target)) ||
      parts.some((part) => names.includes(part))
    ) {
      yield* Effect.fail(denied(path, method))
    }
    if (profile.filesystem.allowWrite.some((rule) => matches(rule, target))) return
    const rule = profile.filesystem.writeRules.findLast((item) => matches(item.rule, target))
    if (rule?.action === "allow") return
    yield* Effect.fail(denied(path, method))
  })
}

export function assertPath(path: string, method: string): Effect.Effect<void, PlatformError.PlatformError> {
  return assertTarget(path, method, canonicalize)
}

export function assertEntry(path: string, method: string): Effect.Effect<void, PlatformError.PlatformError> {
  return assertTarget(path, method, canonicalizeEntry)
}

export function assertWrite(path: string): Effect.Effect<void, PlatformError.PlatformError> {
  return assertPath(path, "assertWrite")
}
