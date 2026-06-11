# Fetch Main And Merge

## Context
- Current branch is `balanced-backpack`, tracking `origin/balanced-backpack`, currently ahead by 1 commit.
- The worktree is dirty with existing JetBrains-related modified/deleted/untracked files plus several untracked plan files.
- Because these local changes may be user/agent work, preserve them and avoid any destructive git commands.

## Execution Plan
1. Re-check `git status --short --branch` immediately before making changes so the pre-merge state is captured.
2. Fetch the latest main ref with `git fetch origin main`.
3. Merge the fetched ref into the current branch with `git merge origin/main`.
4. If Git refuses to merge because local modifications would be overwritten, stop before changing the worktree further and report the exact blocked files. Do not stash, reset, or checkout user changes without explicit approval.
5. If merge conflicts occur, inspect conflict markers and resolve them minimally, preserving existing branch changes unless `origin/main` clearly supersedes them.
6. After resolving conflicts, stage only the conflict-resolution files required to complete the merge and run `git merge --continue` to create the merge commit.
7. Verify the result with `git status --short --branch` and `git log --oneline --decorate -5`.
8. If package files involved in conflicts or automatic merge changes are identifiable, run the smallest relevant check for those touched areas; otherwise state that no targeted code check was applicable.

## Safety Notes
- Do not run `git reset --hard`, `git checkout --`, `git clean`, force-push, or amend commits.
- Do not commit unrelated untracked plan files or unrelated local work.
- Do not push after the merge unless explicitly requested.
