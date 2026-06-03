# JetBrains Session UI Findings Todo

Use this as a planning backlog for the JetBrains session UI performance, memory, thread-safety, and AGENTS.md conformance review.

Last status check: 2026-06-02.

## Current Summary

- Implemented high-priority work: streaming markdown no longer rebuilds the full rendered tree on normal deltas, and hidden cached session UIs are disposed after a configurable timeout while `SessionUpdateQueue` uses a shared coroutine ticker instead of per-session scheduler threads.
- Remaining high-priority work: none. `SessionController` subscription-state mutation is now EDT-confined while RPC event collection remains on background coroutines.
- Remaining non-high-priority work: repaint/revalidate cleanup, lazy collapsed bodies, question editor disposal, EDT annotations/assertions, style callback disposal guards, `SessionUpdateQueue` Swing listener EDT confinement, question body retention, semantic timeline colors, and additional retained Swing regression tests.

## High Priority

- [x] Fix streaming markdown full-tree rebuilds
  - Severity: High
  - Status: Implemented for the high-priority full-tree rebuild issue. Remaining repaint/revalidate cleanup is tracked separately under medium priority.
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/TextView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/ui/md/MdViewHybrid.kt`
  - Issue: `ContentDelta` updates fetch full content and route through `TextView.update()`/`md.set()`, causing markdown to reparse and recreate all child components, including code editors, on each streaming chunk.
  - Implemented: `ContentDelta` carries `created`, `SessionMessageListPanel` routes normal deltas through `appendDelta()`, `TextView.appendDelta()` calls `md.append(delta)`, and `MdViewHybrid` retains compatible HTML/code block views instead of clearing all rendered blocks.
  - Tests/release note: `SessionMessageListPanelTest` covers preserving the `TextView` and markdown component during deltas; `.changeset/retained-jetbrains-markdown.md` documents the user-facing fix.

- [x] Prevent hidden cached sessions from accumulating resources
  - Severity: High
  - Status: Implemented with timed hidden-session disposal and shared coroutine-based queue ticking.
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionSidePanelManager.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionUpdateQueue.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionController.kt`
  - Issue: Inactive cached session UIs keep controller subscriptions, scheduler threads, models, Swing trees, and pending event queues alive. Non-metadata events can accumulate while hidden.
  - Implemented: `kilo.session.inactive.dispose` was removed, `kilo.session.inactive.disposeTimeoutMs` now defaults to 180000 ms, hidden cached `SessionUi` instances are disposed by an EDT timer after the timeout unless shown again, and `SessionUpdateQueue` now uses the controller coroutine scope for its ticker instead of creating a per-queue scheduler thread.
  - Tests/release note: `SessionSidePanelManagerTest` covers hidden timeout disposal, reopen-after-timeout recreation, busy hidden disposal, and permission hidden disposal; `.changeset/hidden-session-timers.md` documents the user-facing fix.

- [x] Confine controller subscription mutation to EDT
  - Severity: High
  - Status: Implemented with EDT-only subscription bookkeeping and background RPC event collection.
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionController.kt`
  - Issue: `prompt()` calls `subscribeEvents()` from a background coroutine after session creation; `subscribeEvents()` mutates `eventJob`, `childJobs`, and `childIds` without EDT confinement.
  - Implemented: `subscribeEvents()`, `subscribeChild()`, `trackChild()`, `drainAutoApprove()`, and subscription cleanup are `@RequiresEdt`/asserted EDT-only. New-session prompt creation hands subscription setup back to EDT before prompt dispatch, and `dispose()` clears subscription state on EDT before cancelling the coroutine scope.
  - Tests/release note: `ChatLoggingFlowTest` covers new-session event subscription, `PromptLifecycleTest` covers duplicate child task parts subscribing once, and `.changeset/jetbrains-controller-subscriptions.md` documents the stability fix.

## Medium Priority

- [x] Remove repaint/revalidate cascades on streaming deltas
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/MessageView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/TextView.kt`
  - Issue: A single content delta can call `refresh()` at child, message, and transcript levels.
  - Implemented: `MessageView.appendDelta()` now delegates to the child part without refreshing the whole message card; leaf text/markdown views still invalidate themselves.

- [x] Lazy-create collapsed tool and reasoning bodies
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/ToolView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/ReasoningView.kt`
  - Issue: Collapsed and even non-expandable views eagerly create `JBTextArea`, `JBScrollPane`, and markdown bodies.
  - Implemented: collapsible secondary views support lazy body suppliers; `ToolView` and `ReasoningView` create text/markdown scroll bodies on first expansion or direct body access and reuse them after collapse.

- [x] Explicitly dispose transient question custom editors
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/question/QuestionView.kt`
  - Issue: `syncPage()` and `hideView()` remove/null `SessionEditorTextField` instances without explicit disposal of editor/listener resources.
  - Implemented: `QuestionView` releases the active custom `EditorTextField` editor before clearing or rebuilding the question page.

- [x] Add EDT annotations/assertions to session model and UI paths
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/model/SessionModel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionSidePanelManager.kt`
  - Issue: `SessionModel` is documented EDT-only, but public APIs and several Swing-mutating methods lack `@RequiresEdt` or assertions.
  - Implemented: public `SessionModel` read/mutation/listener APIs now carry `@RequiresEdt` contracts matching the documented ownership model.

- [x] Guard queued style callbacks after disposal
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt`
  - Issue: theme/editor color listeners schedule `invokeLater { applyStyle(...) }` without checking `disposed` before mutating components.
  - Implemented: queued editor/LAF style callbacks and style application paths now return early once `SessionUi` is disposed.

- [x] Confine `SessionUpdateQueue` Swing listener operations to EDT
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionUpdateQueue.kt`
  - Issue: constructor reads `comp.isShowing` and adds a hierarchy listener without an EDT contract; `dispose()` removes the listener directly.
  - Implemented: `SessionUpdateQueue` marshals hierarchy listener add/remove and `isShowing` reads to EDT, and queued flush work is ignored after disposal.

- [x] Reduce full body rebuilds in question UI
  - Severity: Medium
  - Files: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/question/QuestionView.kt`, `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/base/BaseQuestionView.kt`
  - Issue: question navigation/toggle paths call `body.removeAll()` and recreate rows/buttons/listeners.
  - Implemented: `BaseQuestionView` retains footer buttons by action id, updating text, enabled state, primary style, and handlers in place while still removing stale actions.

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
