# Changelog

## 7.4.0

### Minor Changes

- [#11165](https://github.com/Kilo-Org/kilocode/pull/11165) [`bf67155`](https://github.com/Kilo-Org/kilocode/commit/bf6715594bae4a1160abb7cfdfdedaba4b8358ec) - Enhance draft prompts from the JetBrains chat composer using the configured small model.

## 7.3.42

### Patch Changes

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`c90846a`](https://github.com/Kilo-Org/kilocode/commit/c90846a98938d3cdd666c46294ed4bb4871f7fcd) - Fix JetBrains session scrolling so mouse wheel and keyboard scrolling no longer snap back or bounce near the transcript bottom.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`d505677`](https://github.com/Kilo-Org/kilocode/commit/d505677d88816cf528b64392e23b7ccdddf98a4a) - Prevent the JetBrains session scrollbar from covering transcript content.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`5736a39`](https://github.com/Kilo-Org/kilocode/commit/5736a394597f250f64cf8c684d2426b56ca273ce) - Render glob search results in the JetBrains chat as collapsible tool output with separate directory and pattern rows.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`d1fa450`](https://github.com/Kilo-Org/kilocode/commit/d1fa4506c8b8e65b21cd08e0c6600598366aed0f) - Use matching VS Code-style icons for JetBrains session views.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`952241e`](https://github.com/Kilo-Org/kilocode/commit/952241ee07eebd22717bdf54ce07b3a6c66228af) - Refine JetBrains session card borders so prompt and question surfaces use brighter outlines while reasoning and tool cards use softer default borders.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`b9bff3b`](https://github.com/Kilo-Org/kilocode/commit/b9bff3b69cf27fc7e0d88d411eaa368616fc32d6) - Reset stale hover styling when moving between JetBrains session cards and draw card outlines only while expanded.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`5736a39`](https://github.com/Kilo-Org/kilocode/commit/5736a394597f250f64cf8c684d2426b56ca273ce) - Render grep searches in the JetBrains chat with a dedicated search header that shows stacked, clipped targets.

- [#11015](https://github.com/Kilo-Org/kilocode/pull/11015) [`01f2886`](https://github.com/Kilo-Org/kilocode/commit/01f28861900d4794d6329821f0c9f5c9efdedae3) - Improve mouse wheel scrolling speed in the JetBrains session view.

## 7.3.29

### Patch Changes

## [Unreleased]

## [7.0.1-rc.9] - 2026-06-15

### Added
- feat(agent-manager): add fork action to completed sessions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11063
- feat: default semantic search to LanceDB by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11082
- feat(vscode): add category labels and clarify command titles in packa… by @sylwester-liljegren in https://github.com/Kilo-Org/kilocode/pull/10084
- feat(vscode): use terminal.integrated font settings in Agent Manager xterm by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/11144
- feat(cli): warn when --port is outside discovery range by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/11143
- feat: improve Kilo Console worktree review by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11147
- feat(kilo-vscode): support promoted model deep links by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10827
- feat(vscode): render submitted review comments across chat surfaces by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11173
- feat: support subagent reasoning overrides by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11184
- feat: hide Kilo Gateway models that may train on prompts by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11158
- feat(vscode): add attention sounds by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11098
- feat(cli): align native plan with architect by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/11170
- feat(jetbrains): support prompt and transcript attachments by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11077
- feat(jetbrains): improve shell and markdown rendering by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11095

### Fixed
- fix(cli): sync sessions authenticated by KILO_API_KEY by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10751
- fix(kilo-docs): accept HTTP 202 links by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11016
- fix(cli): allow review follow-up fixes by @alex-alecu in https://github.com/Kilo-Org/kilocode/pull/11064
- fix(cli): keep Console terminals in TUI mode by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11050
- fix(cli): refresh connected provider models by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11006
- fix(cli): avoid repeat compaction from stale totals by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10922
- fix(vscode): restore sessions after channel database regression by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11087
- fix: respect model badge metadata by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11081
- fix(vscode): improve custom question answers by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11051
- fix: prevent duplicate streamed tool calls by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11091
- fix(cli): cap cloud-managed shell command timeouts by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10478
- fix(vscode): keep HTTP status with error title by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11100
- fix(vscode): remove unsupported paste summary setting by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11097
- fix(cli): prevent subagents asking questions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11101
- fix(agent-manager): show local prompt feedback immediately by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11103
- fix(cli): consolidate TUI notifications by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11102
- fix(cli): recover Cerebras, xAI, and OpenAI-compatible SDK upgrades by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11117
- fix(vscode): dismiss stale permission responses by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11118
- fix(agent-manager): preserve named worktrees on collision by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11001
- fix(vscode): disable Copilot in extension dev host by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10997
- fix(agent-manager): avoid blocked native autofocus by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11126
- fix: prevent Bun downgrades during upstream merges by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11124
- fix: improve auto close filter logic by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/11119
- fix(cli): move indexing status to sidebar by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10866
- fix(cli): restore Kilo CLI initialization after upstream merge by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11139
- fix(vscode): route permission replies through request directories by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11133
- fix(vscode): keep binary diffs collapsed by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11151
- fix(vscode): speed up subagent transcript loading by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11104
- fix(cli): inherit reasoning in task subagents by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11160
- fix(shell): use EncodedCommand for PowerShell to fix Windows encoding issues by @senguangd in https://github.com/Kilo-Org/kilocode/pull/11148
- fix(vscode): correct changeset package name by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11162
- fix(cli): restart daemon when console requested host/port don't match by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/11138
- fix(vscode): hide unfinished todo warnings by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11175
- fix(jetbrains): clean up prompt enhancement lifecycle by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11176
- fix: abort Agent Manager prompts during startup by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11152
- fix(vscode): route questions through request directories by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11169
- fix(cli): validate external download inputs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11179
- fix(vscode): use stored API key for model fetches when editing a custom provider by @truffle-dev in https://github.com/Kilo-Org/kilocode/pull/11121
- fix(vscode): restore session state on first redo by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11183
- fix(vscode): clarify doom loop permission labels by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11232
- fix: prevent recursive skill removal by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11240
- fix(vscode): allow removing provider reasoning by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11238
- fix(agent): fix triage model provider path for gpt-5-nano by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/11206
- fix: include prerelease notes in stable releases by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11246
- fix: format provider rate limit errors by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11249
- fix(cli): persist read TUI news by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11171
- fix(cli): prefix generated plan filenames with timestamp by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/11245
- fix(cli): use Kilo packages for upgrades by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11264

### Changed
- release(jetbrains): v7.0.1-rc.8 by @kilo-maintainer[bot] in https://github.com/Kilo-Org/kilocode/pull/11045
- Pause chat auto-scroll on upward wheel over nested content by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11065
- Preserve stop scroll decision on layout changes by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11067
- OpenCode v1.14.51 by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11031
- chore: track local plans by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11062
- perf(cli): seed Agent Manager snapshots from worktree index by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11072
- chore: remove codesearch tool by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11086
- perf(vscode): virtualize bounded transcript rows by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11094
- Indicate when the model list is empty by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11085
- chore: celebrate PR 11111 by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11111
- Refactor scrolling logic by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11115
- docs: update gateway BYOK providers by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11107
- perf(agent-manager): overlap MCP startup with worktree creation by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11074
- perf: speed up large session forks by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11075
- Render streaming assistant rows outside virtualizer by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11153
- perf(agent-manager): virtualize expanded diff reviews by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11154
- ci: clarify auto-close reopen message by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/11163
- Add prompt enhancement to JetBrains by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11165
- Add agent autonomy presets by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/9750
- perf(cli): accelerate durable snapshot initialization by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11178
- OpenCode v1.15.4 by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11090
- refactor(vscode): remove unused extension code by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11231
- chore(sdk): regenerate v2 client by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11234


## [7.0.1-rc.8] - 2026-06-09

### Added

- Display search results and tool output in clearer, more readable JetBrains session cards.

### Fixed

- Improve session transcript scrolling so streaming updates, expanded cards, reasoning blocks, and mouse wheel scrolling preserve the user's position more reliably.
- Make session transcripts easier to scan with tighter spacing, aligned icons, cleaner card outlines, relative search paths, and less visual noise.
- Keep completed reasoning blocks expanded after a response finishes.
- Improve session stability during long-running or cancelled prompts.
- Restore automatic session titles, project skill discovery, and subagent isolation in forked sessions.
- Restore imported cloud session diffs.
- Compact sessions before the configured context limit is exceeded.

### Changed

- Update the bundled Kilo CLI runtime with the latest fixes used by the JetBrains plugin.

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
