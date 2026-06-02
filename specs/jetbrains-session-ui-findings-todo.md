# JetBrains Session UI Findings Todo

Use this as a planning backlog for the JetBrains session UI performance, memory, thread-safety, and AGENTS.md conformance review.

Last status check: 2026-06-02.

## High Priority

- [x] Fix streaming markdown full-tree rebuilds
  - Severity: High
  - Status: Addressed for the high-priority full-tree rebuild issue. Remaining repaint/revalidate cleanup is tracked separately under medium priority.
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/TextView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/ui/md/MdViewHybrid.kt`
  - Issue: `ContentDelta` updates fetch full content and route through `TextView.update()`/`md.set()`, causing markdown to reparse and recreate all child components, including code editors, on each streaming chunk.
  - Plan direction: Restore incremental delta handling where safe, avoid full `set()` for append-only text, and retain/reuse markdown/code block components where possible.
  - Current evidence: `ContentDelta` carries `created`, `SessionMessageListPanel` routes normal deltas through `appendDelta()`, and `MdViewHybrid` retains compatible HTML/code block views instead of clearing all rendered blocks.

- [x] Prevent hidden cached sessions from accumulating resources
  - Severity: High
  - Status: Addressed with timed hidden-session disposal and shared coroutine-based queue ticking.
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionSidePanelManager.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionUpdateQueue.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionController.kt`
  - Issue: Inactive cached session UIs keep controller subscriptions, scheduler threads, models, Swing trees, and pending event queues alive. Non-metadata events can accumulate while hidden.
  - Plan direction: Bound hidden transcript event queues with unconditional timed disposal, and avoid one scheduler thread per session UI.
  - Current evidence: `kilo.session.inactive.dispose` was removed, `kilo.session.inactive.disposeTimeoutMs` now defaults to 180000 ms, hidden cached `SessionUi` instances are disposed by an EDT timer after the timeout unless shown again, and `SessionUpdateQueue` now uses the controller coroutine scope for its ticker instead of creating a per-queue scheduler thread.

- [ ] Confine controller subscription mutation to EDT
  - Severity: High
  - Status: Not addressed; still needs work.
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionController.kt`
  - Issue: `prompt()` calls `subscribeEvents()` from a background coroutine after session creation; `subscribeEvents()` mutates `eventJob`, `childJobs`, and `childIds` without EDT confinement.
  - Plan direction: Run subscription-state mutation on EDT or separate thread-safe subscription state from EDT-only controller state.
  - Current evidence: `prompt()` still calls `subscribeEvents()` from inside `cs.launch`, and `subscribeEvents()` still mutates `eventJob`, `childJobs`, and `childIds` without an EDT assertion or EDT handoff.

## Medium Priority

- [ ] Remove repaint/revalidate cascades on streaming deltas
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/MessageView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/TextView.kt`
  - Issue: A single content delta can call `refresh()` at child, message, and transcript levels.
  - Plan direction: Let changed child views invalidate themselves only when size/paint changes; avoid parent refresh for delegated content updates.

- [ ] Lazy-create collapsed tool and reasoning bodies
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/ToolView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/ReasoningView.kt`
  - Issue: Collapsed and even non-expandable views eagerly create `JBTextArea`, `JBScrollPane`, and markdown bodies.
  - Plan direction: Build only headers initially; create body components on first expansion or first direct access; avoid unused body for non-expandable read views.

- [ ] Explicitly dispose transient question custom editors
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/question/QuestionView.kt`
  - Issue: `syncPage()` and `hideView()` remove/null `SessionEditorTextField` instances without explicit disposal of editor/listener resources.
  - Plan direction: Track editor disposables, call the appropriate `EditorTextField` disposal mechanism, and test repeated custom-row toggle/navigation.

- [ ] Add EDT annotations/assertions to session model and UI paths
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/model/SessionModel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionSidePanelManager.kt`
  - Issue: `SessionModel` is documented EDT-only, but public APIs and several Swing-mutating methods lack `@RequiresEdt` or assertions.
  - Plan direction: Annotate EDT-only methods, add runtime assertions where useful, and ensure listener callbacks remain EDT-only.

- [ ] Guard queued style callbacks after disposal
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt`
  - Issue: theme/editor color listeners schedule `invokeLater { applyStyle(...) }` without checking `disposed` before mutating components.
  - Plan direction: Check `disposed` in queued callbacks and at the start of `applyStyle()`.

- [ ] Confine `SessionUpdateQueue` Swing listener operations to EDT
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionUpdateQueue.kt`
  - Issue: constructor reads `comp.isShowing` and adds a hierarchy listener without an EDT contract; `dispose()` removes the listener directly.
  - Plan direction: Add `@RequiresEdt`/assertions for construction and disposal or marshal Swing listener operations to EDT.

- [ ] Reduce full body rebuilds in question UI
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/question/QuestionView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/base/BaseQuestionView.kt`
  - Issue: question navigation/toggle paths call `body.removeAll()` and recreate rows/buttons/listeners.
  - Plan direction: Retain per-page controls where practical, update existing button/text state, and avoid rebuilding action buttons for simple label/enabled changes.

## Low Priority

- [ ] Replace hardcoded timeline runtime colors with semantic named colors
  - Severity: Low/Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/style/SessionUiStyle.kt`
  - Issue: timeline color tokens use numeric `JBColor(Color(...), Color(...))`; AGENTS.md prefers theme-derived or `JBColor.namedColor(...)` semantic keys.
  - Plan direction: Introduce named color keys with appropriate fallbacks or map to existing platform theme colors.

- [ ] Add focused regression tests for retained Swing behavior
  - Severity: Low
  - Files: `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/**`
  - Issue: Several risks need tests: no parent refresh on no-op deltas, lazy body creation, editor disposal, hidden-session queue bounds, and EDT assertions.
  - Plan direction: Extend existing session UI/controller tests using real IntelliJ EDT fixtures, not mocks.

## Confirmed OK

- No JCEF usage found in inspected session UI paths.
- No Compose usage found in inspected session UI paths.
- No Kotlin UI DSL usage found in inspected session UI paths.
- Session views generally route user actions through `SessionController` rather than direct RPC.
- Controller RPC calls generally run through coroutines rather than blocking EDT.
