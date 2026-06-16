# JetBrains Session Copy Review Fixes Plan

## Goal

Address the branch review findings for the JetBrains session copy work:
- Reduce duplication introduced by the branch.
- Close test coverage gaps around listener disposal and streaming copy toolbar stability.
- Improve conformance with `packages/kilo-jetbrains/AGENTS.md`.
- Verify/fix subtle UI correctness issues in prompt copy toolbar painting and hover overlay hit testing.
- Keep all changes inside `packages/kilo-jetbrains/` plus existing changesets/plans as needed; do not touch `packages/opencode/`.

## Non-Goals

- Do not change copy feature behavior beyond fixing identified issues.
- Do not add new backend, RPC, or shared module code.
- Do not introduce whole-session copy behavior.
- Do not edit generated SDK/client code.

## Implementation Steps

### 1. Consolidate duplicated test `DataSink` helpers

1. Add a small shared test helper in the JetBrains frontend test tree, likely:
   - `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/test/CopyProviderSink.kt`, or another existing test-support package if one exists.
2. The helper should implement `DataSink` and capture `PlatformDataKeys.COPY_PROVIDER`.
3. If useful, include optional capture for `PromptDataKeys.SEND`, or keep a separate prompt-specific sink only where needed.
4. Replace duplicated sink implementations in:
   - `frontend/src/test/kotlin/ai/kilocode/client/session/ui/SessionSelectionCopyTest.kt`
   - `frontend/src/test/kotlin/ai/kilocode/client/ui/md/MdViewHybridTest.kt`
   - `frontend/src/test/kotlin/ai/kilocode/client/session/ui/PromptPanelTest.kt`
5. Remove unused imports created by the replacement.
6. Remove unused `SessionMessageListPanelTest.exit(component)` helper.

### 2. Reduce production copy-provider duplication

1. In `SessionSelection`, replace the duplicated anonymous `TextCopyProvider` bodies with one private helper such as `provider(text: () -> String?)`.
2. Keep behavior identical:
   - default provider copies `selectedText()` only;
   - provider with fallback copies selected text first, then component content.
3. Preserve `ActionUpdateThread.EDT`.

### 3. Add missing EDT annotations for new Swing code

Add `@RequiresEdt` to newly introduced methods that read or mutate Swing state, especially:
- `MessageView.syncCopyToolbar`
- `MessageView.latestAssistantCopyId`
- `MessageView.setPromptHovered`
- `MessageView.paintsPromptToolbar`
- `MessageView.promptToolbarAlignment`
- `MessageView.syncPromptToolbar`
- `MessageView.wrapPrompt`
- `MessageView.installPromptHover`
- `MessageView.inside`
- `MessageView.visit`
- `TextView.setCopyToolbar`
- `TextView.hasCopyToolbar`
- `TextView.copyButton`
- `TextView.copyMarkdown`
- `TextView.syncToolbar`
- `TextView.copyText`
- `TurnView.syncCopyToolbars`

Keep annotations targeted to methods that touch UI state. Do not refactor unrelated existing methods solely for annotation consistency.

### 4. Fix `MessageToolbar` duplicated boolean state

1. Review `MessageToolbar.paint` state.
2. Replace the separate `paint` boolean with state derived from the button or component where practical.
3. Preserve the current UX requirement: prompt toolbar space can remain reserved while the button is visually hidden until hover.
4. If a separate visual-state boolean is still required to preserve layout without painting, rename it to clarify intent, e.g. `painting`, and document why it cannot be derived from `isVisible`.
5. Update tests that assert `paintsPromptToolbar()` to match the final implementation.

### 5. Verify and fix prompt-box painting coordinates

1. Inspect `MessageView.wrapPrompt` and `paintPromptBox` with user prompt attachments or multiple user parts.
2. Add a focused test that creates a user `MessageView` with content above the prompt, then verifies the rounded prompt box is painted at the prompt box's actual location rather than at `(box.x, box.y)` relative to the wrong parent.
3. If the issue reproduces, compute the paint coordinate with Swing coordinate conversion, e.g. convert `(0, 0)` from `promptBox` to `MessageView` coordinates before drawing.
4. Preserve the existing single-prompt behavior and existing prompt hover toolbar behavior.

### 6. Make hover overlay hit testing avoid visibility mutation

1. Revisit `SessionTargetResolver.deepest(root, pt, skip)`.
2. Avoid toggling `skip.isVisible` during every mouse-move hit test if possible.
3. Preferred approach:
   - Resolve deepest component normally.
   - If it is inside `skip`, walk upward/outward or use root coordinates to resolve the underlying sibling without mutating visibility.
   - If a non-mutating implementation is too complex, keep current behavior but add a short comment documenting why it is safe and bounded.
4. Keep tests for skipping the overlay and resolving the underlying copy target.

### 7. Tighten hover overlay bounds guards

1. Review `SessionHoverCopyOverlay.bounds` guard:
   - current condition only bails when `!anchor.isShowing && anchor.parent == null`.
2. If intent is to avoid positioning against hidden or detached anchors, change to a stricter condition such as `if (!anchor.isShowing || anchor.parent == null) return Rectangle()`.
3. Verify this does not break tests where components are constructed but not shown. If tests need synthetic display setup, adjust the tests to exercise the real component tree rather than loosening production guards.

### 8. Add listener disposal and leak coverage

1. Add a test covering disposal of session-scoped global listeners.
2. For `SessionContextMenu.install`, assert at minimum:
   - installing twice on one root is idempotent;
   - root client property is cleared when the parent disposable is disposed.
3. For `SessionHoverCopyOverlay`, assert that disposing the parent/overlay prevents further overlay state changes from synthetic mouse events if practical.
4. If direct listener counting is not available through public API, avoid reflection/internal APIs and test observable cleanup behavior instead.
5. Keep the test in an existing session UI test class if it can reuse real `SessionUi`, otherwise add a small test under `session/ui/`.

### 9. Add streaming stability coverage for copy toolbar

1. Extend `SessionMessageListPanelTest` or add a focused stress test for assistant text streaming.
2. Drive many deltas or repeated content updates through the public model path.
3. Assert:
   - existing `TextView` instance remains stable where expected;
   - markdown component remains stable;
   - copy button / toolbar instance remains stable;
   - component count does not grow with each delta;
   - latest assistant response remains the only visible assistant copy toolbar.
4. Keep this targeted and fast; no need for a full IDE sandbox launch.

### 10. Minor AGENTS.md cleanup

1. Prefer `BorderLayoutPanel` or `JBUI.Panels.simplePanel(...)` for new simple border-layout panels where it improves conformance without churn.
2. Keep `isOpaque = false` only where transparency is required for overlay/paint behavior.
3. Leave `HoverIcon(fill = false)` behavior unchanged.
4. Remove stray blank line before the closing brace in `SessionContextMenu`.

## Verification

Run the smallest relevant checks from `packages/kilo-jetbrains/`:

1. Targeted tests:
   ```bash
   ./gradlew :frontend:test --tests '*SessionSelectionCopyTest' --tests '*PromptPanelTest' --tests '*MdViewHybridTest' --tests '*HistorySessionActionsTest' --tests '*SessionMessageListPanelTest' --tests '*TextViewTest'
   ```
2. Typecheck:
   ```bash
   ./gradlew typecheck
   ```
3. If Java 21 is missing, follow repo guidance before running Gradle:
   ```bash
   java -version
   sdk install java 21-tem
   sdk use java 21-tem
   ```

## Acceptance Criteria

- No changes outside JetBrains-owned package files, tests, changesets, or plans.
- Duplicate test sink implementations are consolidated or clearly minimized.
- New Swing-touching helper methods are annotated with `@RequiresEdt`.
- Prompt copy toolbar painting is verified when prompt content is not at origin.
- Global AWT listener cleanup has focused test coverage.
- Streaming copy toolbar behavior has bounded-component/stability coverage.
- Targeted tests and `./gradlew typecheck` pass, or any remaining failure is documented with exact cause.
