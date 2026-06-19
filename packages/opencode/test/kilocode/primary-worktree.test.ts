import { $ } from "bun"
import { describe, expect, test } from "bun:test"
import path from "path"
import { primaryWorktree } from "../../src/kilocode/primary-worktree"
import { tmpdir } from "../fixture/fixture"

describe("primaryWorktree", () => {
  test("returns the current checkout for a normal repository", async () => {
    await using repo = await tmpdir({ git: true })

    expect(primaryWorktree(repo.path)).toBe(repo.path)
  })

  test("returns the primary checkout for a linked worktree", async () => {
    await using repo = await tmpdir({ git: true })
    const worktree = path.join(repo.path, ".kilo", "worktrees", "feature")
    await $`git worktree add -b primary-worktree-test ${worktree}`.cwd(repo.path).quiet()

    expect(primaryWorktree(worktree)).toBe(repo.path)
  })

  test("returns undefined outside a Git repository", async () => {
    await using dir = await tmpdir()

    expect(primaryWorktree(dir.path)).toBeUndefined()
  })

  test("supports repository paths containing spaces", async () => {
    await using dir = await tmpdir()
    const repo = path.join(dir.path, "repo with spaces")
    await $`git init ${repo}`.quiet()

    expect(primaryWorktree(repo)).toBe(repo)
  })

  test("returns a submodule checkout instead of its internal git directory", async () => {
    await using parent = await tmpdir({ git: true })
    await using child = await tmpdir({ git: true })
    await $`git -c protocol.file.allow=always submodule add ${child.path} sub`.cwd(parent.path).quiet()
    const submodule = path.join(parent.path, "sub")

    expect(primaryWorktree(submodule)).toBe(submodule)
  })
})
