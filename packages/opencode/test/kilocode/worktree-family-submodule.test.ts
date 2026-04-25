import { $ } from "bun"
import { afterEach, describe, expect, test } from "bun:test"
import * as fs from "fs/promises"
import path from "path"
import { Instance } from "../../src/project/instance"
import { WorktreeFamily } from "../../src/kilocode/worktree-family"
import { Log } from "../../src/util"
import { tmpdir } from "../fixture/fixture"

Log.init({ print: false })

afterEach(async () => {
  await Instance.disposeAll()
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

    await Instance.provide({
      directory: submodule,
      fn: async () => {
        const dirs = await WorktreeFamily.list()
        // `git worktree list --porcelain` from inside a submodule reports the
        // gitdir (`<parent>/.git/modules/sub`) as the worktree, so without the
        // submodule guard the actual working tree is missing.
        expect(dirs).toContain(submoduleReal)
      },
    })
  })
})
