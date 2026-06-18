---
"@kilocode/cli": minor
---

Add vim modal editing to the CLI prompt input. Enable it with `"vim": true` in `tui.jsonc`, the `Toggle vim mode` command in the command palette, or the `/vim` slash command. Supports common NORMAL-mode motions (h/j/k/l, w/b/e, 0/^/$, gg/G, counts), edits (x, dd, dw, cw, D, C, r, yy/p, u, Ctrl+r), and insert transitions (i/a/A/I/o/O), with a NORMAL/INSERT indicator and matching cursor shape.
