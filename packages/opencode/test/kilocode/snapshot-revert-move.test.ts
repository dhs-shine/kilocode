import { afterEach, test, expect } from "bun:test"
import { $ } from "bun"
import fs from "fs/promises"
import path from "path"
import { Effect } from "effect"
import { Snapshot } from "../../src/snapshot"
import { Instance } from "../../src/project/instance"
import { Filesystem } from "../../src/util"
import { provideInstance, tmpdir } from "../fixture/fixture"

// Git always outputs /-separated paths internally. Snapshot.patch() joins them
// with path.join (which produces \ on Windows) then normalizes back to /.
const fwd = (...parts: string[]) => path.join(...parts).replaceAll("\\", "/")

afterEach(async () => {
  await Instance.disposeAll()
})

function run<A>(dir: string, body: (snapshot: Snapshot.Interface) => Effect.Effect<A>) {
  return Effect.runPromise(
    Effect.gen(function* () {
      const snap = yield* Snapshot.Service
      return yield* body(snap)
    }).pipe(provideInstance(dir), Effect.provide(Snapshot.defaultLayer)),
  )
}

// Regression test for https://github.com/Kilo-Org/kilocode/issues/9741
//
// When the agent moves a file between folders (A/foo.ts -> B/foo.ts), revert must
// restore the original file to its source folder AND delete it from the destination.
// Git's default rename detection collapses a move to a single destination entry in
// `--name-only` output, which caused revert to only delete the destination while
// silently losing the source. The fix passes `--no-renames` so both paths appear.
test("revert restores original file after move across folders", async () => {
  await using tmp = await tmpdir({
    git: true,
    init: async (dir) => {
      await fs.mkdir(path.join(dir, "A"), { recursive: true })
      await Filesystem.write(`${dir}/A/foo.txt`, "original content")
      await $`git add .`.cwd(dir).quiet()
      await $`git commit --no-gpg-sign -m init`.cwd(dir).quiet()
    },
  })
  await Instance.provide({
    directory: tmp.path,
    fn: async () => {
      const before = await run(tmp.path, (snap) => snap.track())
      expect(before).toBeTruthy()

      // Simulate "AI moves A/foo.txt -> B/foo.txt".
      await fs.mkdir(path.join(tmp.path, "B"), { recursive: true })
      await fs.rename(path.join(tmp.path, "A", "foo.txt"), path.join(tmp.path, "B", "foo.txt"))

      const patch = await run(tmp.path, (snap) => snap.patch(before!))

      // Both source and destination paths must be in the patch list so revert
      // knows to restore the source and delete the destination.
      expect(patch.files).toContain(fwd(tmp.path, "A", "foo.txt"))
      expect(patch.files).toContain(fwd(tmp.path, "B", "foo.txt"))

      await run(tmp.path, (snap) => snap.revert([patch]))

      // Source must be restored with original content intact.
      expect(await fs.readFile(path.join(tmp.path, "A", "foo.txt"), "utf-8")).toBe("original content")

      // Destination must be gone.
      const destExists = await fs
        .access(path.join(tmp.path, "B", "foo.txt"))
        .then(() => true)
        .catch(() => false)
      expect(destExists).toBe(false)
    },
  })
})

// Real sessions can split the delete and the create across different agent steps
// (e.g. a write to B/foo.txt in one step, a delete of A/foo.txt in the next).
// revert() must work across multiple patches from different hashes.
test("revert restores original file across multi-step move", async () => {
  await using tmp = await tmpdir({
    git: true,
    init: async (dir) => {
      await fs.mkdir(path.join(dir, "A"), { recursive: true })
      await Filesystem.write(`${dir}/A/foo.txt`, "original")
      await $`git add .`.cwd(dir).quiet()
      await $`git commit --no-gpg-sign -m init`.cwd(dir).quiet()
    },
  })
  await Instance.provide({
    directory: tmp.path,
    fn: async () => {
      const base = await run(tmp.path, (snap) => snap.track())
      expect(base).toBeTruthy()

      // Step 1: agent creates the new file at B/foo.txt.
      await fs.mkdir(path.join(tmp.path, "B"), { recursive: true })
      await Filesystem.write(`${tmp.path}/B/foo.txt`, "original")
      const patch1 = await run(tmp.path, (snap) => snap.patch(base!))
      const afterStep1 = await run(tmp.path, (snap) => snap.track())

      // Step 2: agent deletes the source A/foo.txt.
      await fs.unlink(path.join(tmp.path, "A", "foo.txt"))
      const patch2 = await run(tmp.path, (snap) => snap.patch(afterStep1!))

      // Each patch's files list should be scoped to what changed in that step.
      expect(patch1.files).toContain(fwd(tmp.path, "B", "foo.txt"))
      expect(patch2.files).toContain(fwd(tmp.path, "A", "foo.txt"))

      // Reverting both patches (in order, oldest first) must end with A/foo.txt restored
      // and B/foo.txt removed.
      await run(tmp.path, (snap) => snap.revert([patch1, patch2]))

      expect(await fs.readFile(path.join(tmp.path, "A", "foo.txt"), "utf-8")).toBe("original")
      const destExists = await fs
        .access(path.join(tmp.path, "B", "foo.txt"))
        .then(() => true)
        .catch(() => false)
      expect(destExists).toBe(false)
    },
  })
})

// A plain rename within the same folder (no directory change) is the same Git
// operation. Without --no-renames this was also broken.
test("revert restores original file after rename within a folder", async () => {
  await using tmp = await tmpdir({
    git: true,
    init: async (dir) => {
      await Filesystem.write(`${dir}/old.txt`, "keep me")
      await $`git add .`.cwd(dir).quiet()
      await $`git commit --no-gpg-sign -m init`.cwd(dir).quiet()
    },
  })
  await Instance.provide({
    directory: tmp.path,
    fn: async () => {
      const before = await run(tmp.path, (snap) => snap.track())
      expect(before).toBeTruthy()

      await fs.rename(path.join(tmp.path, "old.txt"), path.join(tmp.path, "new.txt"))

      const patch = await run(tmp.path, (snap) => snap.patch(before!))
      expect(patch.files).toContain(fwd(tmp.path, "old.txt"))
      expect(patch.files).toContain(fwd(tmp.path, "new.txt"))

      await run(tmp.path, (snap) => snap.revert([patch]))

      expect(await fs.readFile(path.join(tmp.path, "old.txt"), "utf-8")).toBe("keep me")
      const newExists = await fs
        .access(path.join(tmp.path, "new.txt"))
        .then(() => true)
        .catch(() => false)
      expect(newExists).toBe(false)
    },
  })
})
