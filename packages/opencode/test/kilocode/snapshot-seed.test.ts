import { afterEach, expect, test } from "bun:test"
import { $ } from "bun"
import fs from "fs/promises"
import path from "path"
import { Effect } from "effect"
import { Global } from "@opencode-ai/core/global"
import { Hash } from "@opencode-ai/core/util/hash"
import { Snapshot } from "../../src/snapshot"
import { Instance } from "../../src/project/instance"
import { Filesystem } from "../../src/util/filesystem"
import { KiloSnapshotMaterialize } from "../../src/kilocode/snapshot/materialize"
import { disposeAllInstances, provideInstance, tmpdir } from "../fixture/fixture"

const fwd = (...parts: string[]) => path.join(...parts).replaceAll("\\", "/")

async function waitFor(check: () => Promise<boolean>, message: string) {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    if (await check()) return
    await Bun.sleep(25)
  }
  throw new Error(message)
}

function durable(snapshot: Snapshot.Interface) {
  return Effect.gen(function* () {
    const hash = yield* snapshot.track()
    const gitdir = path.join(Global.Path.data, "snapshot", Instance.project.id, Hash.fast(Instance.worktree))
    const alt = path.join(gitdir, "objects", "info", "alternates")
    yield* Effect.promise(() =>
      waitFor(async () => {
        const pending = await Promise.all(
          [alt, `${alt}.materializing`].map((file) => fs.access(file).then(() => true, () => false)),
        )
        return !pending.some(Boolean)
      }, "snapshot alternate was not removed after materialization"),
    )
    return hash
  })
}

function run<A>(dir: string, body: (snapshot: Snapshot.Interface) => Effect.Effect<A>) {
  return Effect.runPromise(
    Effect.gen(function* () {
      const snapshot = yield* Snapshot.Service
      const value = yield* body(snapshot)
      const gitdir = path.join(Global.Path.data, "snapshot", Instance.project.id, Hash.fast(Instance.worktree))
      return { value, gitdir }
    }).pipe(provideInstance(dir), Effect.provide(Snapshot.defaultLayer)),
  )
}

async function setup(dir: string) {
  await $`git config core.autocrlf false`.cwd(dir).quiet()
  await $`git config filter.snapshot-test.clean "tr a-z A-Z"`.cwd(dir).quiet()
  await $`git config filter.snapshot-test.smudge cat`.cwd(dir).quiet()
  await $`git config filter.snapshot-test.required true`.cwd(dir).quiet()
  await Filesystem.write(path.join(dir, "dirty.txt"), "committed dirty\n")
  await Filesystem.write(path.join(dir, "staged.txt"), "committed staged\n")
  await Filesystem.write(path.join(dir, "deleted.txt"), "committed deleted\n")
  await Filesystem.write(path.join(dir, "tracked.log"), "tracked but ignored\n")
  await Filesystem.write(path.join(dir, "filtered.flt"), "committed filtered\n")
  await Filesystem.write(path.join(dir, "assume.txt"), "committed assume\n")
  await Filesystem.write(path.join(dir, "skip.txt"), "committed skip\n")
  await Filesystem.write(path.join(dir, "script.sh"), "#!/bin/sh\nexit 0\n")
  await Filesystem.write(path.join(dir, "huge.bin"), new Uint8Array(2 * 1024 * 1024 + 1))
  await Filesystem.write(path.join(dir, ".gitattributes"), "*.flt filter=snapshot-test\n")
  await $`git add .`.cwd(dir).quiet()
  await $`git commit -m baseline`.cwd(dir).quiet()
  await Filesystem.write(path.join(dir, ".gitignore"), "*.log\n")
  await $`git add .gitignore`.cwd(dir).quiet()
  await $`git commit -m ignore`.cwd(dir).quiet()
}

async function dirty(dir: string) {
  await Filesystem.write(path.join(dir, "dirty.txt"), "user dirty\n")
  await Filesystem.write(path.join(dir, "staged.txt"), "user staged\n")
  await $`git add staged.txt`.cwd(dir).quiet()
  await Filesystem.write(path.join(dir, "staged.txt"), "user unstaged over staged\n")
  await fs.rm(path.join(dir, "deleted.txt"))
  await Filesystem.write(path.join(dir, "untracked.txt"), "user untracked\n")
  await Filesystem.write(path.join(dir, "filtered.flt"), "user filtered\n")
  await Filesystem.write(path.join(dir, "assume.txt"), "user hidden assume\n")
  await Filesystem.write(path.join(dir, "skip.txt"), "user hidden skip\n")
  await $`git update-index --assume-unchanged assume.txt`.cwd(dir).quiet()
  await $`git update-index --skip-worktree skip.txt`.cwd(dir).quiet()
  await Filesystem.write(path.join(dir, "debug.log"), "ignored untracked\n")
  if (process.platform !== "win32") await fs.chmod(path.join(dir, "script.sh"), 0o755)
}

afterEach(async () => {
  await disposeAllInstances()
})

test(
  "regular cold seed matches full snapshot and preserves first-turn reset",
  async () => {
    await using source = await tmpdir({
      git: true,
      init: setup,
    })
    await using root = await tmpdir()
    const seeded = path.join(root.path, "seeded")
    await $`git worktree add --quiet -b snapshot-seed-test ${seeded} HEAD`.cwd(source.path)
    await $`git config extensions.worktreeConfig true`.cwd(source.path).quiet()
    await $`git config --worktree core.sparseCheckout true`.cwd(source.path).quiet()

    await dirty(source.path)
    await dirty(seeded)

    const index = (await $`git rev-parse --path-format=absolute --git-path index`.cwd(seeded).text()).trim()
    const original = await fs.readFile(index)
    const cold = await run(source.path, (snapshot) => snapshot.track())
    const fast = await run(seeded, durable)

    expect(cold.value).toBeTruthy()
    expect(fast.value).toBe(cold.value)
    await expect(fs.access(path.join(cold.gitdir, "objects", "info", "alternates"))).rejects.toThrow()
    const common = (await $`git rev-parse --path-format=absolute --git-common-dir`.cwd(seeded).text()).trim()
    expect(
      (
        await $`git --git-dir=${common} rev-parse --verify --quiet ${KiloSnapshotMaterialize.ref(fast.gitdir)}`
          .nothrow()
          .text()
      ).trim(),
    ).toBe("")
    const dirtyHash = (await $`git hash-object untracked.txt`.cwd(seeded).text()).trim()
    expect((await $`git --git-dir=${common} cat-file -e ${dirtyHash}`.nothrow()).exitCode).not.toBe(0)
    expect(await fs.readFile(index)).toEqual(original)
    expect((await $`git stash create`.cwd(seeded).text()).trim()).toBeTruthy()

    expect((await run(seeded, (snapshot) => snapshot.patch(fast.value!))).value.files).toEqual([])

    await Filesystem.write(path.join(seeded, "dirty.txt"), "assistant dirty\n")
    await Filesystem.write(path.join(seeded, "staged.txt"), "assistant staged\n")
    await Filesystem.write(path.join(seeded, "assume.txt"), "assistant assume\n")
    await Filesystem.write(path.join(seeded, "skip.txt"), "assistant skip\n")
    await Filesystem.write(path.join(seeded, "untracked.txt"), "assistant untracked\n")
    await Filesystem.write(path.join(seeded, "created.txt"), "assistant created\n")
    const patch = (await run(seeded, (snapshot) => snapshot.patch(fast.value!))).value
    expect(patch.files).toEqual(
      expect.arrayContaining([
        fwd(seeded, "dirty.txt"),
        fwd(seeded, "staged.txt"),
        fwd(seeded, "assume.txt"),
        fwd(seeded, "skip.txt"),
        fwd(seeded, "untracked.txt"),
        fwd(seeded, "created.txt"),
      ]),
    )

    await run(seeded, (snapshot) => snapshot.revert([patch]))
    expect(await fs.readFile(path.join(seeded, "dirty.txt"), "utf8")).toBe("user dirty\n")
    expect(await fs.readFile(path.join(seeded, "staged.txt"), "utf8")).toBe("user unstaged over staged\n")
    expect(await fs.readFile(path.join(seeded, "assume.txt"), "utf8")).toBe("user hidden assume\n")
    expect(await fs.readFile(path.join(seeded, "skip.txt"), "utf8")).toBe("user hidden skip\n")
    expect(await fs.readFile(path.join(seeded, "untracked.txt"), "utf8")).toBe("user untracked\n")
    await expect(fs.access(path.join(seeded, "created.txt"))).rejects.toThrow()
    await expect(fs.access(path.join(seeded, "deleted.txt"))).rejects.toThrow()
  },
  { timeout: 15_000 },
)

test("regular seed preserves aged line endings and filtered worktree bytes", async () => {
  await using source = await tmpdir({
    git: true,
    init: async (dir) => {
      await $`git config core.autocrlf true`.cwd(dir).quiet()
      await $`git config filter.snapshot-bytes.clean "tr a-z A-Z"`.cwd(dir).quiet()
      await $`git config filter.snapshot-bytes.smudge "tr A-Z a-z"`.cwd(dir).quiet()
      await $`git config filter.snapshot-bytes.required true`.cwd(dir).quiet()
      await Filesystem.write(path.join(dir, ".gitattributes"), "crlf.txt text\nfiltered.flt filter=snapshot-bytes\n")
      await Filesystem.write(path.join(dir, "crlf.txt"), "one\r\ntwo\r\n")
      await Filesystem.write(path.join(dir, "filtered.flt"), "lower worktree\n")
      await $`git add .`.cwd(dir).quiet()
      await $`git commit -m bytes`.cwd(dir).quiet()
    },
  })
  await using root = await tmpdir()
  const seeded = path.join(root.path, "seeded-bytes")
  await $`git worktree add --quiet -b snapshot-seed-bytes ${seeded} HEAD`.cwd(source.path)
  await $`git config extensions.worktreeConfig true`.cwd(source.path).quiet()
  await $`git config --worktree core.sparseCheckout true`.cwd(source.path).quiet()

  const attrs = Buffer.from("crlf.txt text\nfiltered.flt filter=snapshot-bytes\n")
  const crlf = Buffer.from("one\r\ntwo\r\n")
  const filtered = Buffer.from("lower worktree\n")
  const age = new Date(Date.now() - 10_000)
  for (const dir of [source.path, seeded]) {
    await fs.writeFile(path.join(dir, ".gitattributes"), attrs)
    await fs.writeFile(path.join(dir, "crlf.txt"), crlf)
    await fs.writeFile(path.join(dir, "filtered.flt"), filtered)
    await fs.utimes(path.join(dir, ".gitattributes"), age, age)
    await fs.utimes(path.join(dir, "crlf.txt"), age, age)
    await fs.utimes(path.join(dir, "filtered.flt"), age, age)
    await $`git add .gitattributes crlf.txt filtered.flt`.cwd(dir).quiet()
    await $`git diff --cached --quiet HEAD`.cwd(dir).quiet()
  }

  const cold = await run(source.path, (snapshot) => snapshot.track())
  const fast = await run(seeded, (snapshot) => snapshot.track())
  expect(fast.value).toBe(cold.value)

  for (const dir of [source.path, seeded]) {
    await Filesystem.write(path.join(dir, "crlf.txt"), "assistant\n")
    await Filesystem.write(path.join(dir, "filtered.flt"), "assistant\n")
  }
  const coldPatch = (await run(source.path, (snapshot) => snapshot.patch(cold.value!))).value
  const fastPatch = (await run(seeded, (snapshot) => snapshot.patch(fast.value!))).value
  await run(source.path, (snapshot) => snapshot.revert([coldPatch]))
  await run(seeded, (snapshot) => snapshot.revert([fastPatch]))
  expect(await fs.readFile(path.join(seeded, "crlf.txt"))).toEqual(
    await fs.readFile(path.join(source.path, "crlf.txt")),
  )
  expect(await fs.readFile(path.join(seeded, "filtered.flt"))).toEqual(
    await fs.readFile(path.join(source.path, "filtered.flt")),
  )
})

test(
  "regular primary checkout materializes a durable split-index snapshot",
  async () => {
    await using tmp = await tmpdir({
      git: true,
      init: setup,
    })
    await dirty(tmp.path)
    await $`git update-index --split-index`.cwd(tmp.path).quiet()
    const index = (await $`git rev-parse --path-format=absolute --git-path index`.cwd(tmp.path).text()).trim()
    const original = await fs.readFile(index)

    const result = await run(tmp.path, durable)
    expect(result.value).toBeTruthy()
    const common = (await $`git rev-parse --path-format=absolute --git-common-dir`.cwd(tmp.path).text()).trim()
    const alt = path.join(result.gitdir, "objects", "info", "alternates")
    expect(await fs.readFile(index)).toEqual(original)

    await fs.writeFile(alt, `${path.join(common, "objects")}\n`)
    await fs.rename(alt, `${alt}.materializing`)
    const sourceRef = KiloSnapshotMaterialize.ref(result.gitdir)
    const sourceHash = (await $`git write-tree`.cwd(tmp.path).text()).trim()
    await $`git --git-dir=${common} update-ref ${sourceRef} ${sourceHash}`.quiet()
    await disposeAllInstances()
    await run(tmp.path, (snapshot) =>
      Effect.gen(function* () {
        yield* snapshot.init()
        yield* Effect.promise(() =>
          waitFor(async () => {
            const pending = await Promise.all(
              [alt, `${alt}.materializing`].map((file) =>
                fs.access(file).then(
                  () => true,
                  () => false,
                ),
              ),
            )
            const pinned = (
              await $`git --git-dir=${common} rev-parse --verify --quiet ${sourceRef}`.nothrow().text()
            ).trim()
            return !pending.some(Boolean) && !pinned
          }, "interrupted snapshot materialization did not resume"),
        )
      }),
    )
    expect((await $`git --git-dir=${common} rev-parse --verify --quiet ${sourceRef}`.nothrow().text()).trim()).toBe("")
    expect((await run(tmp.path, (snapshot) => snapshot.patch(result.value!))).value.files).toEqual([])

    const expired = `refs/kilo/snapshots/1/${result.value!}`
    await $`git --git-dir=${result.gitdir} update-ref ${expired} ${result.value!}`.quiet()
    await run(tmp.path, (snapshot) => snapshot.cleanup())
    expect(
      (await $`git --git-dir=${result.gitdir} rev-parse --verify --quiet ${expired}`.nothrow().text()).trim(),
    ).toBe("")
    expect((await $`git --git-dir=${result.gitdir} for-each-ref refs/kilo/snapshots`.text()).trim()).toContain(
      result.value!,
    )

    await $`git --git-dir=${result.gitdir} gc --prune=now`.quiet()
    const objects = path.join(common, "objects")
    const hidden = path.join(common, "objects.hidden")
    await fs.rename(objects, hidden)
    try {
      await $`git --git-dir=${result.gitdir} fsck --connectivity-only --no-dangling --no-reflogs`.quiet()
      await $`git --git-dir=${result.gitdir} cat-file -e ${result.value!}^{tree}`.quiet()
    } finally {
      await fs.rename(hidden, objects)
    }
  },
  { timeout: 15_000 },
)

test("regular seed falls back for sparse checkouts", async () => {
  await using tmp = await tmpdir({
    git: true,
    init: async (dir) => {
      await Filesystem.write(path.join(dir, "inside/tracked.txt"), "tracked\n")
      await Filesystem.write(path.join(dir, "outside/tracked.txt"), "outside\n")
      await $`git add .`.cwd(dir).quiet()
      await $`git commit -m tracked`.cwd(dir).quiet()
      await $`git sparse-checkout set --cone --sparse-index inside`.cwd(dir).quiet()
    },
  })

  const result = await run(tmp.path, (snapshot) => snapshot.track())
  expect(result.value).toBeTruthy()
  await expect(fs.access(path.join(result.gitdir, "objects", "info", "alternates"))).rejects.toThrow()

  const file = path.join(tmp.path, "inside/tracked.txt")
  await Filesystem.write(file, "assistant change\n")
  const patch = (await run(tmp.path, (snapshot) => snapshot.patch(result.value!))).value
  expect(patch.files).toEqual([fwd(file)])
  await run(tmp.path, (snapshot) => snapshot.revert([patch]))
  expect(await fs.readFile(file, "utf8")).toBe("tracked\n")
})
