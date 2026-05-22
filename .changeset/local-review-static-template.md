---
"@kilocode/cli": patch
---

`/local-review` and `/local-review-uncommitted` now pass user input through regular command arguments. Type any extra review focus after the slash command and it is appended to the prompt as `$ARGUMENTS`.
