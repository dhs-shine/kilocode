---
"@kilocode/cli": patch
"kilo-code": patch
---

Fix Revert losing data when the agent moves files between folders. Previously, reverting a task that moved a file would delete it from its new location without restoring the original — now both sides of the move are tracked and the file is correctly restored to its source folder.
