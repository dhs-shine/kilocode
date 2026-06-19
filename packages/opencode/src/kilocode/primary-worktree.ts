import { existsSync } from "fs"
import path from "path"
import { AppFileSystem } from "@opencode-ai/core/filesystem"
import { Effect } from "effect"
import { Git } from "../git"

export const primaryPaths = Effect.fn("PrimaryWorktree.paths")(function* (dir: string, names: readonly string[]) {
  const cwd = AppFileSystem.normalizePath(path.resolve(dir))
  const primary = yield* primaryWorktree(cwd)
  if (!primary || primary === cwd) return []
  return names.map((name) => path.join(primary, name)).filter(existsSync)
})

export const primaryWorktree = Effect.fn("PrimaryWorktree.find")(function* (dir: string) {
  const cwd = AppFileSystem.normalizePath(path.resolve(dir))
  const git = yield* Git.Service
  const run = Effect.fnUntraced(function* (args: string[]) {
    const result = yield* git.run(args, { cwd })
    return result.exitCode === 0 ? result.text() : undefined
  })
  const resolve = (value: string) =>
    AppFileSystem.normalizePath(path.isAbsolute(value) ? path.normalize(value) : path.resolve(cwd, value))
  const line = (value: string | undefined) => value?.replace(/\r?\n$/, "")

  if (line(yield* run(["rev-parse", "--is-inside-work-tree"])) !== "true") return undefined

  const root = line(yield* run(["rev-parse", "--path-format=absolute", "--show-toplevel"]))
  const gitdir = line(yield* run(["rev-parse", "--path-format=absolute", "--git-dir"]))
  const common = line(yield* run(["rev-parse", "--path-format=absolute", "--git-common-dir"]))
  if (!root || !gitdir || !common) return undefined
  if (resolve(gitdir) === resolve(common)) return resolve(root)

  const listing = yield* run(["worktree", "list", "--porcelain", "-z"])
  const fields = listing?.split("\0\0", 1)[0]?.split("\0")
  const worktree = fields?.find((field) => field.startsWith("worktree "))
  if (!worktree || fields?.includes("bare")) return undefined
  return resolve(worktree.slice("worktree ".length))
})
