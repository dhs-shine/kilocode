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

## [7.0.1-rc.10] - 2026-06-17

### Added
- feat(agent-manager): track feature button usage by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11241
- feat(provider): add MiniMax M-series reasoning toggle by @kapelame in https://github.com/Kilo-Org/kilocode/pull/11236
- feat(vscode): expand custom provider configuration by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11239
- feat(vscode): align chat content in readable lane by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11308
- feat(vscode): remove dead layout setting from Display settings by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/11312
- feat(cli): add prompt-training model filter to Kilo Console by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11262
- feat(cli): share codebase indexes across worktrees by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11182
- feat(agent-manager): add close others tab action by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11331
- feat(vscode): render images in diff viewer by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11258
- feat(agent-manager): add quick search for worktrees and sessions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11294
- feat(vscode): support agent switch links by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11317
- feat(cli): show terminal title status indicators by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/10781
- feat(agent-manager): open pull requests with keyboard shortcut by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11367
- feat(jetbrains): add session copy controls by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11324

### Fixed
- fix(ci): skip watch-opencode-releases on forks by @vkeerthivikram in https://github.com/Kilo-Org/kilocode/pull/11269
- fix(jetbrains): cap prompt input growth by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11275
- fix(cli): rebrand OpenCode leftovers by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11270
- fix(jetbrains): clean up restartless unload by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11278
- fix(cli): stabilize Kilo Console worktree flows by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11279
- fix(vscode): count pending submission time by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11113
- fix: support unauthenticated OpenAI-compatible indexing by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11242
- fix(cli): load Atomic Chat as bundled plugin by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11291
- fix(vscode): remove diff viewer corner artifacts by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11297
- fix(cli): improve guidelines for `kilo agent create` by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/11201
- fix(vscode): stabilize expanded edit blocks by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11302
- fix(cli): clean up slow snapshot progress by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11305
- fix(core): always deny tool calls for system agents by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/10091
- fix(cli): restore permission override tests by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11315
- fix(cli): prevent tests from deleting session database by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11316
- fix(vscode): remove chat input separator by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11318
- fix(docs): update Composio toolkits link by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11328
- fix(vscode): keep custom provider picker open after partial add by @truffle-dev in https://github.com/Kilo-Org/kilocode/pull/10195
- fix(webview-ui): ignore Enter during IME composition by @singhvishalkr in https://github.com/Kilo-Org/kilocode/pull/10261
- fix(vscode): restore authenticated speech input by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11295
- fix(vscode): announce compact Settings tabs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11348
- fix(vscode): highlight exact changed characters in diffs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11349
- fix(cli): serialize Codex OAuth refresh across processes by @cooper-oai in https://github.com/Kilo-Org/kilocode/pull/10758
- fix(cli): skip bundled Atomic Chat in plugin loader by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11354
- fix(vscode): show line numbers in edit approval diffs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11357
- fix(cli): identify Kilo in LLM user agent by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11347
- fix(cli): comment out tips for GitHub Actions features Kilo doesn't offer by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11361
- fix(agent-manager): clean up failed session moves by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11358
- fix(jetbrains): harden release skill by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11332
- fix(kilo-docs): copy JetBrains EAP URL without newline by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11310
- fix(vscode): widen chat readable lane from 78ch to 88ch for more content space by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11366
- fix(vscode): improve screen reader model navigation by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11352
- fix: sync remote sessions across extension surfaces by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11351
- fix(cli): bound Telemetry.shutdown so unreachable PostHog endpoint cannot block CLI exit by @truffle-dev in https://github.com/Kilo-Org/kilocode/pull/9807
- fix: silence interrupted session notifications by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11373

### Changed
- release(jetbrains): v7.0.1-rc.9 by @kilo-maintainer[bot] in https://github.com/Kilo-Org/kilocode/pull/11271
- Add opencode changeset generator by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11257
- docs: clarify checkpoint behavior without Git by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11233
- docs(kilo-docs): document inline session renaming by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10824
- docs(kilo-docs): clarify Mistral AI BYOK label by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11290
- test(jetbrains): make session timeout tests deterministic by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11292
- ci: disable upstream issue maintenance workflows by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11289
- [codex] Add code edit block display setting by @Githubguy132010 in https://github.com/Kilo-Org/kilocode/pull/11080
- ux(cli): change `/copy` command to copy last message by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/10929
- Docs: update mobile Kilo Pass link by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10700
- Docs: document webhook trigger overview by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10702
- Docs: document organization custom modes by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10693
- Docs: add DoltHub coverage by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10699
- Docs: update Kilo Pass links by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10697
- refactor(kilo-console): use Kilo UI boundary by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/11288
- test(cli): remove session cancellation timing races by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11299
- ci: skip unrelated JetBrains tests by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11293
- docs: document VS Code model deep link protocol handler by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/11189
- docs: rename Development Tools to Settings, add Linear and Composio integration pages by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/11313
- Revise README for improved clarity and details by @bturcotte520 in https://github.com/Kilo-Org/kilocode/pull/11326
- docs(customize): clarify agent permission rules by @singhvishalkr in https://github.com/Kilo-Org/kilocode/pull/11141
- ci: add jetbrains container image and fix containers.yml trigger by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11306
- test(jetbrains): inject UI timers per test by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11309
- ci: extract JetBrains test workflow by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11339
- chore: remove 33 unused dependencies across monorepo by @idreesmuhammadqazi-create in https://github.com/Kilo-Org/kilocode/pull/10993
- chore: remove stale sst-env.d.ts artifacts by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11344
- chore(cli): remove unused @hono/zod-validator dependency by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11345
- perf(jetbrains): eliminate fixed waits from session tests by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11343
- adding alternate language readmes by @bturcotte520 in https://github.com/Kilo-Org/kilocode/pull/11335
- Polish VS Code tool call previews by @Drixled in https://github.com/Kilo-Org/kilocode/pull/11146
- Add JetBrains provider settings management by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11221
- ci: complete the JetBrains test container by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11372


## [7.0.1-rc.9] - 2026-06-15

### Added

- Add prompt enhancement support.
- Support prompt and transcript attachments, including paste, drop, preview, and editor tab opening flows.

### Fixed

- Improve shell and markdown rendering, including code block spacing, terminal block retention, shell command highlighting, and session layout polish.

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
