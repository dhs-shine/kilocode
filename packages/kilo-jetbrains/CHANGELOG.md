# Changelog

## 7.4.6

### Patch Changes

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`42a4966`](https://github.com/Kilo-Org/kilocode/commit/42a49667a946a2f4f22df44b82aa5c3ff11f9aee) - Return keyboard focus to the JetBrains prompt after clicking inline session dialog actions.

- [#12105](https://github.com/Kilo-Org/kilocode/pull/12105) [`8ceeb0f`](https://github.com/Kilo-Org/kilocode/commit/8ceeb0fb990911f5dc4647f7f9d75b26f5ce0ec4) - Stop orphaned Kilo CLI processes when JetBrains IDEs close, including binaries that ignore graceful shutdown.

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`39cec20`](https://github.com/Kilo-Org/kilocode/commit/39cec2063572368462acd3347bbf588991f366e2) - Refresh the JetBrains prompt input chrome when switching IDE themes.

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`04a1aa1`](https://github.com/Kilo-Org/kilocode/commit/04a1aa1b123f1b64591786d32fd58a30019fe007) - Polish JetBrains prompt focus and copy toolbar positioning.

- [#12104](https://github.com/Kilo-Org/kilocode/pull/12104) [`c1b206b`](https://github.com/Kilo-Org/kilocode/commit/c1b206b161b8376355fdb2c16a7f4e972e7806fd) - Show rollback/redo progress inline (on the message and redo controls) with a cancel action instead of a full-screen loading overlay.

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`7e7ab7e`](https://github.com/Kilo-Org/kilocode/commit/7e7ab7e795ca0922f16bfa549d088c23fe631c2f) - Support rollback and redo controls in JetBrains sessions and clarify when reverted changes can be redone.

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`c1415d2`](https://github.com/Kilo-Org/kilocode/commit/c1415d2879bd7eb38910df43f7593cd641dbd343) - Clarify in JetBrains rollback that only the conversation was reverted when snapshots are disabled.

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`eb8950c`](https://github.com/Kilo-Org/kilocode/commit/eb8950c1efc3386ebc479c09298187768c6e0cc5) - Polish JetBrains session message toolbar alignment, rollback icon, and copy tooltips.

- [#12059](https://github.com/Kilo-Org/kilocode/pull/12059) [`8ea3f10`](https://github.com/Kilo-Org/kilocode/commit/8ea3f10495e28c8a131b805d51f8f7524895148b) - Increase spacing before non-initial user prompts in the JetBrains session transcript.

## [Unreleased]

## [7.0.5] - 2026-07-14

### Added
- feat: allow sandbox network destinations by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12075
- feat(vscode): add file picker to @ mention dropdown by @sylwester-liljegren in https://github.com/Kilo-Org/kilocode/pull/12028
- feat(vscode): add in-chat search for the current session by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12155
- feat(vscode): add persistent local session tabs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10466
- feat: report active CLI and VS Code app and session presence by @eshurakov in https://github.com/Kilo-Org/kilocode/pull/12159
- feat(vscode): highlight transcript parts from timeline bars by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12065
- feat(agent-manager): add prompt enhancer to worktree dialog by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11687
- feat: bump session ingest version to v2 by @pandemicsyn in https://github.com/Kilo-Org/kilocode/pull/12117

### Fixed
- fix(cli): use filePath in Gemini prompt by @Githubguy132010 in https://github.com/Kilo-Org/kilocode/pull/12101
- fix(cli): resolve latest CLI release when GitHub latest points to non-CLI tag by @umi008 in https://github.com/Kilo-Org/kilocode/pull/12148
- fix(agent-manager): accept Windows workspace path casing by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12152
- fix(cli): show Kilo Gateway login rate limit message by @mjnaderi in https://github.com/Kilo-Org/kilocode/pull/11837
- fix: restore GPT-5.6 reasoning summaries by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12092
- fix(cli): explain Gemini API key rejections by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12162
- fix(cli): sanitize unsupported regex lookarounds by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12153
- fix(cli): avoid independent worktree indexing scans by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12164
- fix(cli): preserve sanitized tool schema inputs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12166
- fix(cli): resolve curl upgrade version from npm dist-tag instead of GitHub releases/latest by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12167
- fix: sanitize empty Gemini object requirements by @jstar0 in https://github.com/Kilo-Org/kilocode/pull/11955
- fix(cli): skip thinkingLevel for Gemma models on Google provider by @umi008 in https://github.com/Kilo-Org/kilocode/pull/12149
- fix(cli): block project markdown secret exfiltration by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12168
- fix(vscode): focus prompt after closing sidebar tab by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12176
- fix(vscode): preserve focus after closing inactive tab by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12177
- fix(cli): preserve provider errors from chunked compaction by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12156
- fix(indexing): retry remote embedder validation on fail by @shssoichiro in https://github.com/Kilo-Org/kilocode/pull/12187
- fix(jetbrains): facelift session controls by @kirillk in https://github.com/Kilo-Org/kilocode/pull/12180
- fix(cli): preserve forked session variants by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12175
- fix(cli): release Windows file handles after reads by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12097
- fix(agent-manager): keep subagents out of tabs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11536
- fix(agent-manager): inherit sandbox for tool-started sessions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11783
- fix(docs): exclude t.me links from lychee link checker by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12197
- fix(agent-manager): stop sessions when closing tabs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/11424
- fix(cli): surface invalid Kilo indexing.model as an error instead of silently falling back by @rakshith1928 in https://github.com/Kilo-Org/kilocode/pull/12128
- fix(vscode): remember initial prompts in history by @chrarnoldus in https://github.com/Kilo-Org/kilocode/pull/12201
- fix(cli): allow trusting global skill directories by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12160
- fix(cli): enforce read permissions for file mentions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12158
- fix(vscode): improve question option contrast by @LEN5010 in https://github.com/Kilo-Org/kilocode/pull/11922
- fix(cli): temporarily disable session export by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/12205
- fix(cli): isolate model cache refresh from caller cancellation by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12203
- fix(jetbrains): restore legacy v5 migration import by @kirillk in https://github.com/Kilo-Org/kilocode/pull/12188

### Changed
- release(jetbrains): v7.0.4 by @kilo-maintainer[bot] in https://github.com/Kilo-Org/kilocode/pull/12119
- chore(script): reconcile team list with active maintainers by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12154
- test(sandbox): stabilize fragmented ClientHello coverage by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12151
- docs(kilo-docs): exclude Google AI Studio API keys page from link checker by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12163
- OpenCode v1.16.2 by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/12088
- test(jetbrains): reduce unit test runtime by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/12171
- Fix remote CLI session control from mobile by @iscekic in https://github.com/Kilo-Org/kilocode/pull/12189
- docs(vscode): improve agent behaviour setting descriptions (#7668) by @Tamsi in https://github.com/Kilo-Org/kilocode/pull/11868


## [7.0.4] - 2026-07-10

### Fixed

- Stop orphaned Kilo Core processes on Windows so closing the IDE no longer leaves a lingering `kilo serve` process or blocks the next IDE launch.
- Improve JetBrains CLI shutdown ordering so app close kills the process tree before closing streams, preventing Windows shutdown deadlocks.

## [7.0.3] - 2026-07-10

### Added

- Add rollback redo controls in JetBrains sessions so reverted changes can be restored from the chat UI.
- Add inline revert progress in JetBrains sessions, including localized status text and safer cancellation handling.
- Add Kilo Core support for localized commit-message generation, AI image generation, large bash-output pruning, and improved model-usage display.

### Fixed

- Harden Kilo Core startup and shutdown so startup failures show clearer diagnostics, app close stops the CLI process, and lingering child processes are cleaned up more reliably.
- Fix workspace reload recovery so stale reload state no longer disrupts the session connection.
- Fix JetBrains rollback and revert flows so prompt focus, scroll state, diff order, and turn state are preserved more reliably.
- Fix Kilo Core Bedrock SSO credential resolution and commit-message error handling when no changes are available.

### Changed

- Update the JetBrains plugin to download Kilo Core 7.4.5.

## [Unreleased]

## [7.0.2] - 2026-07-07

### Added

- First GA release of the native Kilo extension for JetBrains IDEs.
- Download the pinned Kilo Core release at runtime instead of bundling CLI binaries, keeping the JetBrains plugin smaller while verifying downloaded archives before use.
- Show Kilo Core runtime details from the JetBrains plugin so users can see which Core release is active.

### Fixed

- Improve JetBrains runtime CLI download reliability by pruning stale binaries, using the shell environment for PATH resolution, and surfacing exact release-resolution failures.

### Changed

- Polish JetBrains chat UI with auto-collapsing reasoning previews, clearer retry/offline footer state, and more balanced prompt, code, question, todo, history, and popup spacing.
- Show the active routed model name and remote status more consistently in CLI runtime surfaces.

## [7.0.2-rc.2] - 2026-07-07

### Added

- Show compact previews for collapsed reasoning blocks so long assistant reasoning stays readable without taking over the transcript.
- Add clearer Kilo Core runtime information and diagnostics for release download failures.

### Fixed

- Resolve the CLI executable using the user's shell environment so custom PATH setups work when sessions start from JetBrains.
- Keep retry and offline status visible in the session footer while preserving transcript context.
- Prevent oversized header popups by capping preview content.

### Changed

- Download the required Kilo Core release at runtime and prune stale cached runtime binaries automatically.
- Polish JetBrains chat spacing, prompt input behavior, question/todo layout, history scrolling, code block padding, and session background colors.

## [7.0.2-rc.1] - 2026-07-07

### Added

- Download the pinned Kilo Core release at runtime instead of bundling every CLI binary in the JetBrains plugin, keeping the Marketplace package smaller while still verifying downloaded artifacts.

## [7.0.1] - 2026-07-06

### Added

- Launch the first public Kilo JetBrains release with native JetBrains sessions and remote development support.

## [7.0.1-rc.15] - 2026-07-06

### Fixed

- Improve transcript rendering, prompt focus styling, settings clicks, and prompt picker interactions.

## [7.0.1-rc.14] - 2026-07-02

### Added

- Add Agent Behavior settings
- Show richer model picker details, including routed model information and clearer model badges.
- Show Kilo Pass usage, bonus credits, renewal dates, and top-up actions in the JetBrains user profile.

### Fixed

- Recover backend startup more reliably when event streams stall, reconnect, or are interrupted by stale failures.
- Resolve workspaces by project ID to avoid cross-project session confusion.
- Improve CLI recovery, config paths, and `.kilo` config directory handling.

## [7.0.1-rc.13] - 2026-06-23

### Added

- Add slash command and file mention completion in the prompt.
- Add support for clickable and explainable `@file` mentions in the prompt.

### Fixed

- Fix prompt undo/redo behavior and restore prompt focus after history navigation.
- Fix lazy session creation to avoid duplicate initialization.
- Fix prompt-training model disclosure.

### Changed

- Update the bundled CLI to include upstream OpenCode 1.15.13 changes.

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
