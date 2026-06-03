# Changelog

## 7.3.23

### Patch Changes

- [#10847](https://github.com/Kilo-Org/kilocode/pull/10847) [`cdf46c9`](https://github.com/Kilo-Org/kilocode/commit/cdf46c97354630e2f1b392092ee0ffcc18b19640) - Clarify when free-model data may be used for training and identify it with a brain circuit icon.

- [#10839](https://github.com/Kilo-Org/kilocode/pull/10839) [`7e856a5`](https://github.com/Kilo-Org/kilocode/commit/7e856a57c4fd3dc29effb64be41d9dbd7554c346) - Improve JetBrains model settings dropdown direction, logged-out profile branding, and CLI menu labeling.

- [#10839](https://github.com/Kilo-Org/kilocode/pull/10839) [`138a58f`](https://github.com/Kilo-Org/kilocode/commit/138a58f80f75bb287f4b75b16a0bb04f11d09e88) - Improve the JetBrains tool window settings menu with config file shortcuts and separate CLI restart and reinstall actions.

- [#10839](https://github.com/Kilo-Org/kilocode/pull/10839) [`2183102`](https://github.com/Kilo-Org/kilocode/commit/2183102843a896675b8855504f5964808851ea42) - Fix model settings layout and keep pending model selections visible while saving.

- [#10801](https://github.com/Kilo-Org/kilocode/pull/10801) [`076d8ab`](https://github.com/Kilo-Org/kilocode/commit/076d8ab1366a1c33f5c5eb944d1e5c9faf288d0c) - Add Feedback & Support to the JetBrains empty session screen.

## [Unreleased]

## [7.0.1-rc.5] - 2026-06-03

### Added

- Core: Added the experimental Kilo Console, including animated loading screens and a more reliable packaged startup experience.
- Core: Added `kilo profile` for checking the active Kilo account and team balance.
- Core: Added read-tool support for Jupyter notebooks, DOCX documents, and XLSX spreadsheets.
- Core: Made semantic indexing available without requiring an experimental feature flag.
- Core: Show a running indicator while subagents are working.
- Core: Added adaptive thinking support for Claude Opus 4.8.
- Plugin: Added Feedback & Support entry points from the empty session screen.
- Plugin: Added model and configuration settings, including config file shortcuts and separate CLI restart and reinstall actions.

### Fixed

- Core: Restored Agent Manager session forks and improved how agents reuse relevant completed-session context.
- Core: Kept queued plan prompts, post-compaction replies, and cloud session previews from stalling or appearing out of order.
- Core: Restored session sharing links, Kilo Gateway-backed Next Edit completions, and accurate Gateway error reporting.
- Core: Reduced unnecessary background port scans and fixed session listing when launching from a git submodule.
- Core: Prevented semantic indexes from rebuilding unnecessarily after restart.
- Core: Fixed export dialog checkbox clicks, multiline TUI alerts, exact `kilo run --raw` input handling, and Architect plan file saving.
- Core: Kept Kilo Console terminals stable while the console refreshes.
- Plugin: Prevented stale backend events from affecting sessions after a restart.
- Plugin: Improved chat code blocks and made long or streaming session transcripts faster and more stable.


## [7.0.1-rc.4] - 2026-05-29

### Added

- Initial JetBrains plugin release with a native Kilo Code tool window.
- Chat sessions with streamed responses, tool output, reasoning, markdown, todos, and plan follow-ups.
- Native mode/model selection, account sign-in, permission prompts, and question flows.
- Local and cloud session history with search, reopen, rename/delete local sessions, and repository filtering.
- Migration wizard for legacy JetBrains plugin settings and chat history.
- Bundled Kilo CLI runtime for macOS, Linux, and Windows.
