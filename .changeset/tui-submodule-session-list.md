---
"@kilocode/cli": patch
---

Fix empty TUI session list when launching kilo from inside a git submodule. `git worktree list --porcelain` reports the submodule's gitdir (`<repo>/.git/modules/<sub>`) instead of the working tree, so the worktree-family filter dropped every session whose directory was the actual submodule path. Include `Instance.worktree` in the returned set so submodule sessions stay in scope.
