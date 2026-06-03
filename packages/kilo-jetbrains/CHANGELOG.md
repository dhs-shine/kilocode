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
- feat(cli): guide Agent Manager recall usage by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10669
- feat: remove semantic indexing experimental gate by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10668
- feat(vscode): add BYOK Gateway link in provider connect dialog footer by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10674
- feat(cli): read Jupyter notebooks as cell content by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10733
- feat(cli): support DOCX text extraction in read tool by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10737
- feat(config): add kilo-jetbrains package to upstream merge config by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10742
- feat(cli): support XLSX text extraction in read tool by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10740
- feat(cli): treat opus 4.8 as adaptive thinking model like 4.7 by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10735
- feat(jetbrains): expand telemetry and environment logging by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10748
- feat: introduce experimental Kilo Console by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10754
- feat(cli): show running spinner in subagent footer by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10310
- feat(cli): add profile command by @Githubguy132010 in https://github.com/Kilo-Org/kilocode/pull/10298
- feat(cli): add animated console loading screens by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10865
- feat(jetbrains): feedback UI by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10801
- feat(kilo-jetbrains): add model and config settings by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10839

### Fixed
- fix(vscode): add context handoff for forked sessions by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10609
- fix(indexing): prevent LanceDB metadata type coercion causing full re-index on restart by @barzhomi in https://github.com/Kilo-Org/kilocode/pull/10703
- fix(vscode): make model selection accessible to screen readers by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10688
- fix(vscode): make session history accessible to screen readers by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10686
- fix(agent-manager): keep slow snapshot initialization non-blocking by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10721
- fix(vscode): replace dashes with spaces in marketplace keywords by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10691
- fix(vscode): detect Windows speech input devices by @Ipsumlorem in https://github.com/Kilo-Org/kilocode/pull/10594
- fix(cli): route share links through Kilo session API by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10736
- fix(vscode): keep large Changes diffs smooth while scrolling by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10730
- fix(jetbrains): emit log warnings before updating state values by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10743
- fix: stop publishing without changelog updates by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10749
- fix(vscode): preserve diff scroll during agent edits by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10782
- fix(vscode): surface backend crash recovery by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10783
- fix(cli): skip background port scans in VS Code by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10786
- fix(jetbrains): reduce flaky CI builds by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10774
- fix(cli): allow clearing agent variant overrides by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10792
- fix(kilo-docs): use stable external links by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10793
- fix(cli): preserve context for queued plan prompts by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10789
- fix(cli): include working tree in WorktreeFamily.list for submodules by @truffle-dev in https://github.com/Kilo-Org/kilocode/pull/9499
- fix(vscode): declare Pierre worker dependency for builds by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10753
- fix(cli): limit background process port scans by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10796
- fix(cli): toggle export dialog checkboxes on mouse click by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10306
- fix(tui): handle newlines in DialogAlert messages by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10191
- fix(cli): preserve --raw atoms verbatim in run handler (#9622) by @truffle-dev in https://github.com/Kilo-Org/kilocode/pull/9653
- fix(vscode): sort permission exceptions by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10808
- fix: address vulnerability on vitest dependency by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/10815
- fix(vscode): keep tool handoffs out of virtual history by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10816
- fix: correct architect use of plan exit to save the plan file correctly by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/10798
- fix: suppress false incomplete response warnings by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10800
- fix(vscode): defer collapsed historical tool details by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10810
- fix(vscode): show shell command description in permission approval prompt by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10692
- fix(vscode): harden marketplace skill installs by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10806
- fix(cli): restore Agent Manager session forks by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10829
- fix(jetbrains): ignore stale SSE callbacks after restart by @johnnyeric in https://github.com/Kilo-Org/kilocode/pull/10832
- fix: keep post-compaction replies ordered by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10831
- fix(vscode): preserve inline review drafts across diff refreshes by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10846
- fix: restore cloud session previews by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10849
- fix: use brain circuit for data disclosure by @iscekic in https://github.com/Kilo-Org/kilocode/pull/10847
- fix(ci): cap stalled unit jobs at 45 minutes by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10854
- fix(sdk): preserve nullable Kilo gateway fields by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10861
- fix(cli): restore Kilo Gateway next edit proxying by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10863
- fix(cli): preserve Kilo gateway error statuses by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10864
- fix(cli): restore packaged console startup by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10883
- fix: clarify free model training disclosure by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10880
- fix(jetbrains): add editor-backed code blocks and optimize session UI by @kirillk in https://github.com/Kilo-Org/kilocode/pull/9976

### Changed
- Remove wrappers for Provider service by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10658
- Docs: update KiloClaw public docs by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10701
- Remove provider/provider.ts from promise facade allowlist by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10716
- refactor(cli): remove legacy Vcs facade by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10717
- docs: clarify shared snapshot guard scope by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10718
- Docs: add VS Code proxy and CA troubleshooting by @evanjacobson in https://github.com/Kilo-Org/kilocode/pull/10698
- refactor(cli): remove Permission promise facade by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10715
- docs: clarify per-directory AGENTS.md loading behavior by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10707
- refactor(cli): remove SessionPrompt promise facade by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10726
- test(vscode): enforce scoped webview accessibility coverage by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10680
- refactor(cli): remove Session promise facade by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10731
- refactor(cli): make notebook read routing explicit by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10739
- refactor(cli): remove Question compatibility facade by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10727
- docs: refresh README hero by @jobrietbergen in https://github.com/Kilo-Org/kilocode/pull/10734
- release(jetbrains): v7.0.1-rc.4 by @kilo-maintainer[bot] in https://github.com/Kilo-Org/kilocode/pull/10706
- docs(jetbrains): link v7 early access guide by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10745
- docs(kilo-docs): improve tabbed install docs by @kirillk in https://github.com/Kilo-Org/kilocode/pull/10747
- chore: backfill changelogs for v7.3.16 through v7.3.18 by @lambertjosh in https://github.com/Kilo-Org/kilocode/pull/10750
- chore: sync @ai-sdk/openai version across packages by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10777
- docs(cli): link Agent Manager guides from config skill by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10809
- OpenCode v1.14.42 by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10790
- refactor(vscode): extract editor actions from KiloProvider by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10814
- Session export capture by @iscekic in https://github.com/Kilo-Org/kilocode/pull/10611
- revert: sort permission exceptions (#10808) by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10818
- Revert "fix: suppress false incomplete response warnings" by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10826
- perf(vscode): reduce long-session switch overhead by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10823
- docs: correct skill discovery URL format and manifest documentation by @IamCoder18 in https://github.com/Kilo-Org/kilocode/pull/10756
- Keep Kilo Console terminals stable during refresh by @catrielmuller in https://github.com/Kilo-Org/kilocode/pull/10833
- Add scanning by @emilieschario in https://github.com/Kilo-Org/kilocode/pull/10799
- refactor(vscode): extract shared diff review UI by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10853
- vscode: add opencode and open code as keywords by @kilo-code-bot[bot] in https://github.com/Kilo-Org/kilocode/pull/10870
- ci(codeql): split Kotlin analysis into dedicated workflow by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10869
- Upgrade @types/node by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10860
- Bump bun from 1.3.13 to 1.3.14 by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10884
- OpenCode v1.14.46 by @marius-kilocode in https://github.com/Kilo-Org/kilocode/pull/10822
- Extract Marketplace logic from KiloProvider by @imanolmzd-svg in https://github.com/Kilo-Org/kilocode/pull/10850


## [7.0.1-rc.4] - 2026-05-29

### Added

- Initial JetBrains plugin release with a native Kilo Code tool window.
- Chat sessions with streamed responses, tool output, reasoning, markdown, todos, and plan follow-ups.
- Native mode/model selection, account sign-in, permission prompts, and question flows.
- Local and cloud session history with search, reopen, rename/delete local sessions, and repository filtering.
- Migration wizard for legacy JetBrains plugin settings and chat history.
- Bundled Kilo CLI runtime for macOS, Linux, and Windows.
