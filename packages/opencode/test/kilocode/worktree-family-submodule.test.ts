import { $ } from "bun"
import { afterEach, describe, expect, test } from "bun:test"
import { Effect, ManagedRuntime } from "effect"
import * as fs from "fs/promises"
import path from "path"
import { Git } from "../../src/git"
import { WorktreeFamily } from "../../src/kilocode/worktree-family"
import { Project } from "../../src/project/project"
import * as Log from "@opencode-ai/core/util/log"
import { disposeAllInstances, provideInstance, tmpdir } from "../fixture/fixture"

Log.init({ print: false })

const project = Project.Service.of({
  init: () => Effect.void,
  fromDirectory: () => Effect.die(new Error("unused")),
  discover: () => Effect.void,
  list: () => Effect.succeed([]),
  get: () => Effect.succeed(undefined),
  update: () => Effect.die(new Error("unused")),
  initGit: (input) => Effect.succeed(input.project),
  setInitialized: () => Effect.void,
  sandboxes: () => Effect.succeed([]),
  addSandbox: () => Effect.void,
  removeSandbox: () => Effect.void,
})

async function withGit<T>(body: (rt: ManagedRuntime.ManagedRuntime<Git.Service, never>) => Promise<T>) {
  const rt = ManagedRuntime.make(Git.defaultLayer)
  try {
    return await body(rt)
  } finally {
    await rt.dispose()
  }
}

afterEach(async () => {
  await disposeAllInstances()
})

describe("WorktreeFamily.list — git submodule", () => {
  test("returns the submodule's working tree, not its gitdir", async () => {
    await using parent = await tmpdir({ git: true })
    await using child = await tmpdir({ git: true })

    // `protocol.file.allow=always` so the local clone is permitted, then commit
    // the .gitmodules entry so the submodule is part of the parent's history.
    await $`git -c protocol.file.allow=always submodule add ${child.path} sub`.cwd(parent.path).quiet()
    await $`git commit -m "add submodule"`.cwd(parent.path).quiet()

    const submodule = path.join(parent.path, "sub")
    const submoduleReal = await fs.realpath(submodule)

    await withGit(async (rt) => {
      const dirs = await rt.runPromise(
        WorktreeFamily.list().pipe(Effect.provideService(Project.Service, project), provideInstance(submodule)),
      )
      // `git worktree list --porcelain` from inside a submodule reports the
      // gitdir (`<parent>/.git/modules/sub`) as the worktree, so without the
      // submodule guard the actual working tree is missing.
      expect(dirs).toContain(submoduleReal)
    })
  })
})
