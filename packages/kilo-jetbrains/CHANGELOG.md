# Changelog

## 7.3.29

### Patch Changes

## [Unreleased]

## [7.0.1-rc.8] - 2026-06-09

### Added

- Add dedicated JetBrains renderers for search tools and tool output so session transcripts show structured results with editor-style bodies.
- Add support for reading `.ods` OpenDocument Spreadsheet files.
- Suggest project skill slash commands in the CLI, including disambiguation for similarly named commands.
- Show Terminal Bench model details in model metadata.

### Fixed

- Preserve JetBrains session scroll intent across transcript updates, expand/collapse actions, streaming reasoning, and mouse wheel scrolling.
- Improve JetBrains session transcript usability with tighter spacing, aligned icons and progress footers, cleaner card outlines, regular-text search targets, relative search paths, and hidden markdown separators.
- Keep completed reasoning expanded while bounding streaming reasoning resources to avoid unnecessary editor retention.
- Fix CLI release target isolation, baseline startup crashes, Alpine musl validation, and morphsdk module resolution in release builds.
- Reject unsupported webfetch images with clearer tool errors.
- Write strict JSON when saving MCP config.
- Restore session export after the temporary disablement.
- Restore automatic session titles, subagent isolation in session forks, project skill discovery in worktree sessions, and reset costs in forked sessions.
- Prevent visible planning chat from being copied into new sessions.
- Restore cloud session diffs on import.
- Compact before the configured context threshold is exceeded.
- Prevent prompt queue memory leaks when cancelling sessions without active tails.

### Changed

- Update the bundled CLI to include OpenCode v1.14.48 compatibility and release build hardening.
- Refresh npm package metadata and README content used by the CLI package.

## [7.0.1-rc.7] - 2026-06-04

### Fixed

- Fixed JetBrains release notes rendering so notes from multiple releases display correctly.

## [7.0.1-rc.6] - 2026-06-03

### Fixed

- Model picker now highlights models that can be used for training.

## [7.0.1-rc.5] - 2026-06-03

### Added

- Added Feedback & Support entry points to the empty session screen
- Model and configuration settings, including config file shortcuts and separate CLI restart and reinstall actions.

### Fixed

- Prevented stale backend events from affecting sessions after a restart.
- Improved chat code blocks and made long or streaming session transcripts faster and more stable.

## [7.0.1-rc.4] - 2026-05-29

### Added

- Initial JetBrains plugin release with a native Kilo Code tool window.
- Chat sessions with streamed responses, tool output, reasoning, markdown, todos, and plan follow-ups.
- Native mode/model selection, account sign-in, permission prompts, and question flows.
- Local and cloud session history with search, reopen, rename/delete local sessions, and repository filtering.
- Migration wizard for legacy JetBrains plugin settings and chat history.
- Bundled Kilo CLI runtime for macOS, Linux, and Windows.
