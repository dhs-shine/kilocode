# Changelog

## 7.3.47

### Patch Changes

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`ef7aa7f`](https://github.com/Kilo-Org/kilocode/commit/ef7aa7fdf854f8a50681b56cb56377fa7763b18d) - Fix JetBrains provider settings after OAuth/connect actions by waiting through transient backend reloads and allowing longer OAuth exchanges.

- [#11095](https://github.com/Kilo-Org/kilocode/pull/11095) [`9eddaf1`](https://github.com/Kilo-Org/kilocode/commit/9eddaf17126a63822307e9a52d9a32794eca5176) - Highlight shell tool commands in JetBrains chat transcripts.

- [#11095](https://github.com/Kilo-Org/kilocode/pull/11095) [`5058050`](https://github.com/Kilo-Org/kilocode/commit/50580501679ea0900c2102c0509575e89f15a48e) - Improve markdown readability in JetBrains chat transcripts.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`52dfa54`](https://github.com/Kilo-Org/kilocode/commit/52dfa5453ed6080270f9e07cf6e9cafd8df75cd7) - Hide generated read-tool payload lines from JetBrains prompt bubbles while keeping attachments and assistant tool output visible.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`9f8f698`](https://github.com/Kilo-Org/kilocode/commit/9f8f698e38f8317b0f19d1f7406b0d6cc9777ad9) - Show JetBrains prompt attachments in one horizontal scrolling row in session history.

- [#11324](https://github.com/Kilo-Org/kilocode/pull/11324) [`bc7f9b0`](https://github.com/Kilo-Org/kilocode/commit/bc7f9b05e8ca61ebf59d5a069923eef59c900a15) - Show a hover copy button for JetBrains session code and tool output blocks.

- [#11324](https://github.com/Kilo-Org/kilocode/pull/11324) [`358135f`](https://github.com/Kilo-Org/kilocode/commit/358135f69ef6a4985cdd7aab0f1a2a8a0b631c27) - Add copy buttons below JetBrains session prompts and assistant responses.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`db96e31`](https://github.com/Kilo-Org/kilocode/commit/db96e31e655d77ef54ade03c32688218ca2e0e58) - Show provider names on JetBrains model picker buttons for non-Kilo Gateway models.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`49339a2`](https://github.com/Kilo-Org/kilocode/commit/49339a2583f6c51db9d1bfdc3f37ee5a4185b8a9) - Support pasting files and images into JetBrains chat prompts as attachments.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`9f8f698`](https://github.com/Kilo-Org/kilocode/commit/9f8f698e38f8317b0f19d1f7406b0d6cc9777ad9) - Show JetBrains prompt attachments inside the prompt bubble with previews and open embedded attachments in editor tabs.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`a8b127e`](https://github.com/Kilo-Org/kilocode/commit/a8b127e0ca8a7f29a11d03e548e78d084ccc3aa6) - Support sending file and image attachments from the JetBrains chat prompt.

- [#11275](https://github.com/Kilo-Org/kilocode/pull/11275) [`3c319a5`](https://github.com/Kilo-Org/kilocode/commit/3c319a59a24a7fbf4a1d65eb88d1572ec178694b) - Limit JetBrains prompt input growth to the session while preserving scrolling for long prompts.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`b6bbb83`](https://github.com/Kilo-Org/kilocode/commit/b6bbb839e613a82e65ff445498e128a90851f8a5) - Show Connect for available JetBrains providers without explicit auth metadata, keep only actually configured providers disconnectable, and reduce provider settings diagnostics to debug logs.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`b0183f9`](https://github.com/Kilo-Org/kilocode/commit/b0183f984611df1b09145aa9716e948ee6bb4780) - Prefer remote-safe provider OAuth methods in JetBrains and show device-code authorization details when available.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`d8b6efd`](https://github.com/Kilo-Org/kilocode/commit/d8b6efd58c0ee66b1f71e484282993aad27fc08e) - Show cancellable OAuth progress in JetBrains provider settings and prevent starting another provider action while one is running.

- [#11278](https://github.com/Kilo-Org/kilocode/pull/11278) [`62e42c1`](https://github.com/Kilo-Org/kilocode/commit/62e42c1efbeecd243c473e3cdde8d8a6ac55efc5) - Stop Kilo backend processes and clear JetBrains UI resources during restartless plugin unloads.

- [#11324](https://github.com/Kilo-Org/kilocode/pull/11324) [`b4864eb`](https://github.com/Kilo-Org/kilocode/commit/b4864ebd43f211fe3f594edb29798f2f5d48b599) - Fix copying selected text from JetBrains session views.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`793cf93`](https://github.com/Kilo-Org/kilocode/commit/793cf934b72e7f9d71be3f23af058d88be67a9d3) - Support dropping files anywhere in a JetBrains chat session to add them to the prompt.

- [#11095](https://github.com/Kilo-Org/kilocode/pull/11095) [`bb31723`](https://github.com/Kilo-Org/kilocode/commit/bb31723e7353c0649b9854812f9f803e04d92156) - Polish session header controls and align session view icons.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`709a53c`](https://github.com/Kilo-Org/kilocode/commit/709a53cda17da96a74cbd5a8bb88a9c6a78bd28e) - Open embedded JetBrains message attachments in frontend-managed Kilo editor tabs with loading and connection retry states.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`ad3be6c`](https://github.com/Kilo-Org/kilocode/commit/ad3be6cae999b440277fd8c660ba1b5eead07020) - Organize JetBrains provider settings into connected, popular, and all-provider sections, hide custom-provider creation, and prevent Kilo Gateway disconnects from provider settings.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`a8b127e`](https://github.com/Kilo-Org/kilocode/commit/a8b127e0ca8a7f29a11d03e548e78d084ccc3aa6) - Render file attachments as attachment cards in JetBrains prompts and session history.

- [#11095](https://github.com/Kilo-Org/kilocode/pull/11095) [`a73ee53`](https://github.com/Kilo-Org/kilocode/commit/a73ee5329cf4455d33d8c8fd363ccf83b46a3cdb) - Render JetBrains shell tool output with markdown code blocks.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`1c7d5ca`](https://github.com/Kilo-Org/kilocode/commit/1c7d5ca7d373770e8d731177e0b851253d9c7d57) - Restore popular provider suggestions in JetBrains provider settings when provider metadata is unavailable.

- [#11221](https://github.com/Kilo-Org/kilocode/pull/11221) [`987da27`](https://github.com/Kilo-Org/kilocode/commit/987da2728731e1da1c974996b5bcddafe745cea7) - Show shared provider descriptions and provider icons in JetBrains and VS Code provider settings.

- [#11077](https://github.com/Kilo-Org/kilocode/pull/11077) [`2f9c6ec`](https://github.com/Kilo-Org/kilocode/commit/2f9c6ecdd00c49b9c53a8e72bf8971cabf51821a) - Open embedded transcript attachments in stable Kilo editor tabs.

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

## [7.0.1-rc.13] - 2026-06-24

### Added
- feat: add account profile to Kilo Console by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11434
- feat: indicate BYOK availability in model pickers by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11421
- feat(cli): add a privacy filter to Console model Explore by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11301
- feat(server,daemon): display local and network URLs when binding to 0.0.0.0 by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/11028
- feat(cli): support compact and summarize in kilo run --command by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10005
- feat(cli): improve daemon stop workflow by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/11478
- feat(cli): search local session transcripts by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11468
- feat(gateway): default autocomplete to Mercury Next Edit by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11036
- feat(vscode): let users hide disabled indexing button by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11570
- feat(cli): centralize sandboxed agent writes by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11548
- feat(jetbrains): add slash commands and mention completion by @kirillk in https://github.com/Kilo-Org/kilocode/pull/11327
- feat(sandbox): enforce macOS network isolation by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11603

### Fixed
- fix(vscode): widen chat readable lane by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11451
- fix(vscode): tolerate duplicate panel command registration by @mjnaderi in https://github.com/Kilo-Org/kilocode/pull/10940
- fix(vscode): keep diff identifiers intact by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11423
- fix(agent-manager): stabilize concurrent subagent rendering by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11433
- fix: silence subagent completion sounds by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11453
- fix: hide provider errors after session revert by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11455
- fix: add vercel bypass header to opencode release watcher by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/11460
- fix(agent-manager): speed up worktree hover cards by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11463
- fix(console): add context to settings fields by @nyxst4ck in https://github.com/Kilo-Org/kilocode/pull/11446
- fix(vscode): keep chat streaming after revert by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11464
- fix(tui): show docs URL in dialog when browser cannot open on headless systems by @grandmaster451 in https://github.com/Kilo-Org/kilocode/pull/11319
- fix(cli): improve headless docs dialog UX by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/11212
- fix(cli): scale local-review sub-agents based on diff size and complexity by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/11155
- fix(cli): preserve custom subagent permissions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11475
- fix(jetbrains): disclose prompt-training models by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11467
- fix(cli): restore read tool streaming by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11533
- fix(cli): support MiniMax M3 thinking controls by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11552
- fix(cli): support GLM 5.2 thinking variants by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/11555
- fix(cli): avoid encoded PowerShell commands by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11526
- fix(core): create the default `.kilo/plans` directory automatically when Plan mode starts by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/11505
- fix(vscode): silence auto-approved permissions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11573
- fix(cli): isolate sandboxed worktree writes by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11584
- fix(vscode): preserve code index across updates by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11588
- fix(vscode): use fim fallback for chat autocomplete by @markijbema in https://github.com/Kilo-Org/kilocode/pull/11592

### Changed
- release(jetbrains): v7.0.1-rc.12 by @kilo-maintainer[bot] in https://github.com/Kilo-Org/kilocode/pull/11435
- docs: document prompt-training model visibility by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11416
- docs(vscode): clarify custom provider protocol selection by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/11418
- docs(cli): propagate chunkTimeout to streamText and document provider options by @Fatty911 in https://github.com/Kilo-Org/kilocode/pull/9795
- cli: skip automatic browser launch for kilo console on headless Linux by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/11026
- OpenCode v1.15.13 by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/11450
- docs: document REVIEW.md sub-agent guidance by @alex-alecu in https://github.com/Kilo-Org/kilocode/pull/11524
- test(ci): run unit suite on macOS by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11567
- test(ci): focus macOS PR tests on Darwin coverage by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11574


## [7.0.1-rc.12] - 2026-06-18

### Added

- Provider settings management, including searchable provider lists, API-key configuration, OAuth provider login, provider enable/disable controls, disconnect actions, and shared provider metadata.
- Add copy controls to session messages so prompts and assistant responses can be copied directly from the transcript.
- Share codebase indexes across worktrees so Agent Manager and worktree sessions can use semantic search without duplicating the full index.

### Fixed

- Keep long JetBrains prompt input usable by capping growth, preserving scrolling, and hiding soft-wrap glyphs.
- Copy actions correctly in session.

### Changed

- Update the bundled CLI runtime to OpenCode 1.15.9

## [7.0.1-rc.11] - 2026-06-17

### Added

- Provider settings management, including provider catalog sections, provider descriptions, provider settings actions, disconnect flows, provider auth handling, and provider/model picker improvements.
- Session copy controls for chat messages.

### Fixed

- Cap JetBrains prompt input growth and hide soft wrap glyphs in the prompt field.
- Keep JetBrains provider toolbars and authentication overlays fixed, and improve provider API key dialog sizing.
- Clean up restartless unload behavior.
- Silence interrupted session notifications across clients.
- Always deny tool calls for system agents.

## [7.0.1-rc.10] - 2026-06-17

### Added

- Provider settings management, including provider catalog sections, provider descriptions, provider settings actions, disconnect flows, provider auth handling, and provider/model picker improvements.
- Session copy controls for chat messages.

### Fixed

- Cap JetBrains prompt input growth and hide soft wrap glyphs in the prompt field.
- Keep JetBrains provider toolbars and authentication overlays fixed, and improve provider API key dialog sizing.
- Clean up restartless unload behavior.
- Silence interrupted session notifications across clients.
- Always deny tool calls for system agents.

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
