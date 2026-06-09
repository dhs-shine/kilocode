# Changelog

## 7.3.29

### Patch Changes

## [Unreleased]

## [7.0.1-rc.8] - 2026-06-09

### Added
- feat: add JetBrains release skill by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10917
- feat: show Terminal Bench model details by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10879
- feat: add .ods (OpenDocument Spreadsheet) support to read tool by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10761
- feat: add Kilo auto-close workflow by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/11040

### Fixed
- fix(vscode): replace brain-circuit with book-open-check for training disclosure by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10913
- fix(cli): isolate release target builds by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10935
- fix(cli): reject unsupported webfetch images by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/10901
- fix(cli): write strict JSON for MCP config by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10933
- fix(cli): temporarily disable session export by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10924
- fix(vscode): preserve Explorer after window reload by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10951
- fix(cli): prevent Bun ESM splitter crash and fix Alpine musl validation by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10955
- fix(cli): use build plugin to redirect morphsdk ESM barrel to CJS by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10956
- fix(cli): resolve morphsdk client.cjs via exported subpath by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10957
- fix(cli): disable Bun code-splitting to fix baseline release crash by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10958
- fix: support custom plan exit paths by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/10952
- fix(cli): ignore legacy models snapshots by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10995
- fix(vscode): route worktree permission approvals correctly by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10787
- fix(cli): restore automatic session titles by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10998
- fix(cli): restore subagent isolation in session forks by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11000
- fix(cli): discover project skills in worktree sessions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11004
- fix(core): avoid copying visible planning chat into new sessions by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/10991
- fix(kilo-docs): link directly to MiniMax console by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11007
- fix(ui): restore pointer cursors for clickable controls by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10999
- fix(kilo-docs): use stable DeepSeek links by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11017
- fix(cli): compact before configured context threshold by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11010
- fix: prevent memory leak in KiloSessionPromptQueue.cancel for sessions without active tails by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10109
- fix: agent-manager model sync on config change by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10094
- fix(cli): reset forked session costs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11034
- fix(vscode): keep concurrent Agent Manager streams responsive by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11033
- fix(vscode): remove stale speech-to-text setting by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11039
- fix(jetbrains): improve session transcript scrolling by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10962

### Changed
- release(jetbrains): v7.0.1-rc.7 by @kilo-maintainer[bot] in https://github.com/Kilo-Org/kilocode/pull/10915
- docs(kilo-docs): update JetBrains EAP guidance by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10916
- docs(cli): refresh npm package README by @jobrietbergen in https://github.com/Kilo-Org/kilocode/pull/10905
- Prevent false auto-scroll pauses from programmatic scroll events by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10942
- Maintain scroll position on container resize by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10944
- Skip pausing auto-scroll on question answer by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10946
- Prevent content shift on turn end by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10950
- docs(security): reflect Security Agent availability in docs by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10898
- docs: update NVIDIA Nemotron model to Ultra 550B by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10911
- Fix CLI models snapshot release validation by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10949
- refactor: fix intel mac runner by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10961
- Suggest skill slash commands in CLI by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10925
- Restore cloud session diffs on import by @iscekic in https://github.com/Kilo-Org/kilocode/pull/10948
- test(cli): remove network-dependent session export case by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11012
- ci: raise VS Code Storybook heap limit by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11014
- Rename Mercury autocomplete model labels by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/10992
- OpenCode v1.14.48 by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10996
- test(cli): fix prompt queue message ID fixture by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11032
- perf(ui): render streamed markdown incrementally by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11038
- perf(vscode): coalesce task timeline performance optimization by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11037
- revert(cli): re-enable session export by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11011
- Improve JetBrains session usability by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11015


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
