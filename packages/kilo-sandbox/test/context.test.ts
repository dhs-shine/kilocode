import { afterAll, beforeAll, describe, expect, test } from "bun:test"
import { mkdir, mkdtemp, realpath, rm, symlink } from "node:fs/promises"
import { tmpdir } from "node:os"
import path from "node:path"
import { Deferred, Effect } from "effect"
import { assertWrite, current, enabled, grantWrite, run, type Profile } from "../src"

function makeProfile(
  allowWrite: Profile["filesystem"]["allowWrite"],
  denyWrite: Profile["filesystem"]["denyWrite"] = [],
  denyNames: Profile["filesystem"]["denyNames"] = [],
  writeRules: Profile["filesystem"]["writeRules"] = [],
): Profile {
  return {
    filesystem: { allowWrite, denyWrite, writeRules, denyNames },
    network: { mode: "allow", allowedHosts: [] },
    environment: { deny: [], set: {} },
  }
}

describe("sandbox profile context", () => {
  let root = ""

  beforeAll(async () => {
    root = await realpath(await mkdtemp(path.join(tmpdir(), "kilo-sandbox-context-")))
  })

  afterAll(async () => {
    await rm(root, { recursive: true, force: true })
  })

  test("is disabled outside run and exposes the normalized current profile inside run", async () => {
    expect(await Effect.runPromise(enabled)).toBe(false)
    const value = await Effect.runPromise(
      run(makeProfile([{ path: root, kind: "subtree" }]), Effect.all([enabled, current])),
    )
    expect(value[0]).toBe(true)
    expect(value[1]?.filesystem.allowWrite[0]?.path).toBe(root)
    expect(await Effect.runPromise(current)).toBeUndefined()
  })

  test("keeps grants isolated across concurrent runs", async () => {
    const target = path.join(root, "granted.txt")
    const ready = await Effect.runPromise(Deferred.make<void>())
    const profile = makeProfile([])
    const result = await Effect.runPromise(
      Effect.all(
        [
          run(
            profile,
            Effect.gen(function* () {
              yield* grantWrite(target)
              yield* Deferred.succeed(ready, undefined)
              yield* assertWrite(target)
              return true
            }),
          ),
          run(
            profile,
            Effect.gen(function* () {
              yield* Deferred.await(ready)
              const error = yield* assertWrite(target).pipe(Effect.flip)
              return error.reason._tag
            }),
          ),
        ],
        { concurrency: "unbounded" },
      ),
    )
    expect(result).toEqual([true, "PermissionDenied"])
  })

  test("lets an explicit grant override an ordered ask rule", async () => {
    const target = path.join(root, "approved", "file.txt")
    const profile = makeProfile(
      [],
      [],
      [],
      [
        { rule: { path: root, kind: "subtree" }, action: "allow" },
        { rule: { path: path.join(root, "approved"), kind: "subtree" }, action: "ask" },
      ],
    )
    const before = await Effect.runPromise(run(profile, assertWrite(target).pipe(Effect.flip)))
    expect(before.reason._tag).toBe("PermissionDenied")
    await Effect.runPromise(
      run(
        profile,
        Effect.gen(function* () {
          yield* grantWrite(path.join(root, "approved"), "subtree")
          yield* assertWrite(target)
        }),
      ),
    )
  })

  test("applies deny rules before overlapping allows and grants", async () => {
    const denied = path.join(root, ".git")
    const target = path.join(denied, "config")
    const profile = makeProfile([{ path: root, kind: "subtree" }], [{ path: denied, kind: "subtree" }])
    const error = await Effect.runPromise(
      run(
        profile,
        Effect.gen(function* () {
          yield* grantWrite(target)
          return yield* assertWrite(target).pipe(Effect.flip)
        }),
      ),
    )
    expect(error.reason._tag).toBe("PermissionDenied")
  })

  test("applies denied path names to future external grants", async () => {
    const target = path.join(root, "external", ".git", "config")
    const error = await Effect.runPromise(
      run(
        makeProfile([], [], [".git"]),
        Effect.gen(function* () {
          yield* grantWrite(path.join(root, "external"), "subtree")
          return yield* assertWrite(target).pipe(Effect.flip)
        }),
      ),
    )
    expect(error.reason._tag).toBe("PermissionDenied")
  })

  test("canonicalizes the longest existing ancestor across symlinks", async () => {
    const allowed = path.join(root, "allowed")
    const outside = path.join(root, "outside")
    await mkdir(allowed)
    await mkdir(outside)
    await symlink(outside, path.join(allowed, "link"), "junction")

    const error = await Effect.runPromise(
      run(
        makeProfile([{ path: allowed, kind: "subtree" }]),
        assertWrite(path.join(allowed, "link", "new.txt")).pipe(Effect.flip),
      ),
    )
    expect(error.reason._tag).toBe("PermissionDenied")
  })

  test("resolves dangling symlinks before authorizing a write", async () => {
    const allowed = path.join(root, "dangling-allowed")
    const outside = path.join(root, "dangling-outside.txt")
    await mkdir(allowed)
    await symlink(outside, path.join(allowed, "link"))

    const error = await Effect.runPromise(
      run(makeProfile([{ path: allowed, kind: "subtree" }]), assertWrite(path.join(allowed, "link")).pipe(Effect.flip)),
    )
    expect(error.reason._tag).toBe("PermissionDenied")
  })
})
