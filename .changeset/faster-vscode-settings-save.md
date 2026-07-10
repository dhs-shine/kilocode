---
"kilo-code": patch
"@kilocode/cli": patch
---

Speed up VS Code settings saves by draining and disposing worktree instances concurrently, then finishing once config writes succeed.
