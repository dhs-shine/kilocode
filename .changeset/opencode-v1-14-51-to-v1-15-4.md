---
"@kilocode/cli": patch
"kilo-code": patch
---

Changes from opencode v1.14.51 to v1.15.4 upstream:

## Core

### Improvements

- Added an Effect-based core event system for more complete event delivery across sessions and integrations.
- Clarified how to recover when the npm package is installed without its native binary.
- Reduced unnecessary prompting around shell, task, and todo flows.

### Bugfixes

- Ignored invalid exports in custom tool modules instead of failing tool loading.
- Ignored project instruction lookup errors so sessions keep loading when project instruction discovery fails.
- Fixed versioned event projector lookups so event replay uses the right handlers.
- Avoid duplicate consecutive entries in prompt history.
- Show full config validation errors during TUI startup instead of a generic failure.
- Fixed npm installs so the CLI can recover and fetch the right native binary on more setups.
- Fixed multiline `@` mentions in prompts.
- Preserved custom tool metadata from Zod schemas.
- Preserved custom tool argument descriptions in generated schemas.
- Fixed file watching in repos where `.git` is a symlink. (@kagura-agent)
- Fixed sync events not reaching project-scoped subscribers in injected instances.
- Reduced wasted work when reading very large files after output truncation.
- Fixed project-scoped bus events so file watcher and update notifications reach the right instance.
- Fixed custom LSP servers not sending refresh events after they initialize.
- Hid background subagent task instructions unless experimental background mode is enabled.

## TUI

### Improvements

- Added a collapsed thinking view that can be expanded inline.
- Added pinned sessions with quick-switch slots in the session picker.
- Newly pinned sessions now stay at the end of the pinned list instead of jumping to the top.
- Made Markdown H1 headings easier to distinguish.

### Bugfixes

- Fixed thinking mode defaults so reasoning starts collapsed consistently.
- Limited session quick-switching to pinned sessions.
- Fixed Markdown table rendering in chat output.
- Fixed `opencode run --agent` resolving project-local agents.
- Fixed async commands losing the active instance context, which could break agent generation and GitHub-driven runs.
