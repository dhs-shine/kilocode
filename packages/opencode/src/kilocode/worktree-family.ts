// kilocode_change - new file
import { Instance } from "../project/instance"
import { Project } from "../project/project"
import { Filesystem } from "../util/filesystem"
import { Git } from "../git"

export namespace WorktreeFamily {
  export async function list() {
    if (Instance.project.vcs !== "git") {
      return [Filesystem.resolve(Instance.directory)]
    }

    const listed = await Git.run(["worktree", "list", "--porcelain"], {
      cwd: Instance.worktree,
    })

    if (listed.exitCode === 0) {
      const dirs = listed
        .text()
        .split("\n")
        .map((line) => line.trim())
        .flatMap((line) => {
          if (!line.startsWith("worktree ")) return []
          return [Filesystem.resolve(line.slice("worktree ".length).trim())]
        })

      if (dirs.length > 0) {
        // In a git submodule, `git worktree list --porcelain` reports the
        // gitdir (`<repo>/.git/modules/<sub>`) instead of the actual working
        // tree, so the parsed list never contains the directory sessions are
        // recorded under. Including Instance.worktree keeps submodule sessions
        // in scope without affecting normal repos (already present) or linked
        // worktrees (also already present).
        dirs.push(Filesystem.resolve(Instance.worktree))
        return [...new Set(dirs)]
      }
    }

    const dirs = [Instance.worktree, ...(await Project.sandboxes(Instance.project.id))]
    return [...new Set(dirs.map((dir) => Filesystem.resolve(dir)))]
  }
}
