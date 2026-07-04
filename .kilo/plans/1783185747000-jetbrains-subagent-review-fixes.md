# TaskToolView / subagent review fixes

## Goal

Close the review findings on the `massive-fontina` branch's JetBrains subagent
(`TaskToolView`) work: remove dead code, add the missing streaming stress/leak test, shrink
test-only production seams, fix a theme-in-constructor border, and add direct `SessionModel`
child-tool bookkeeping tests. These are behavior-preserving cleanups plus new tests — the
inline-subagent feature itself already works and is controller-tested.

## Scope

All work is confined to Kilo-owned JetBrains paths (path contains `kilo`, so **no
`kilocode_change` markers needed**):

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/tool/TaskToolView.kt`
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/model/SessionModel.kt`
  (tests only; no production change unless a bug surfaces)
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptPanel.kt`
  (optional, item 7)
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/tool/ShellToolView.kt`
  (optional, item 8)
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/views/**`
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/model/**`

Out of scope: the `MdViewHybrid`/`MdProjector` refactor (already landed and well-tested), the
six shipped UI fixes' runtime behavior, and any change to the `AbstractSessionPartView` base
or sibling tool views. Do not touch `SessionModel`'s child-tool logic unless item 6 tests
reveal a real defect.

## Constraints

- Swing on the IntelliJ platform: all component creation/mutation stays on the EDT; keep the
  existing `@RequiresEdt` intent. No background-thread UI mutation.
- Tests extend `BasePlatformTestCase` for a real Application + EDT. **No mocks** of the EDT,
  threading, or platform types — assert against the real Swing tree, as existing tests do.
- Prefer single-word names; avoid `let`/`else`/`try-catch` where the style guide says so.
- Do not add new test-only production accessors. When a test needs state, prefer walking the
  real component tree from the test (as `SessionMessageListPanelTest` / `ShellToolViewTest`
  already do) over exposing a new seam.
- Implementation requires source edits: hand off to an implementation-capable agent. Validate
  with `./gradlew typecheck test --tests "ai.kilocode.client.session.views.*"` and
  `--tests "ai.kilocode.client.session.model.*"` from `packages/kilo-jetbrains/` (Java 21).

## Decisions (recommended; change before implementing if you disagree)

1. **`TaskToolView.controlCount()` is dead** — the only caller of any `controlCount()` is
   `ToolViewTest` against `ToolView`. **Recommendation: delete it.**
2. **Test-only accessors**: many `TaskToolView` methods are used only by tests
   (`rowLabels`, `bodyScrollValue/bodyScrollBottom/setBodyScrollValue`, `horizontalPolicy`,
   `verticalPolicy`, `bodyInsets`, `rowTitleColor(id)`, and the `public` `rowCount`/
   `bodyCreated`). **Recommendation: (a) delete the ones a test can replace by walking the
   real tree; (b) for the few that are awkward to derive from the tree, keep them but make
   them `internal` to match sibling views (`ToolView`, `ReadToolView`, `ReasoningView`).** Do
   not expand the public surface beyond the sibling pattern. `labelText()` (used by
   `dumpLabel()`) and `bodyVisible()`/`bodyMaxRows()` (used internally) stay — they have
   product use.
3. **`TaskToolViewStressTest` is required** (AGENTS.md "Stress and Leak Tests for Streaming
   UI"). **Recommendation: add it**, mirroring `ReasoningViewStressTest`.
4. **`TaskBody` glyph-derived left inset** is captured once in the constructor
   (`glyph.preferredSize.width`). **Recommendation: recompute on `updateUI()`** (or drop the
   glyph-width dependency) so the indent tracks LaF/DPI changes. Keep it minimal.
5. **Items 7 (PromptPanel default scope) and 8 (padPopup direct-children scan) are optional
   hardening.** **Recommendation: do 7 if cheap, defer 8** unless the popup tree changes —
   both are low risk today.

## Task list (ordered)

### Phase 1 — Dead code

1. Delete `TaskToolView.controlCount()` (`TaskToolView.kt:100-101`). Grep-confirm zero
   references in `frontend/src` and `frontend/src/test` before removing.

### Phase 2 — Stress + leak test (safety net for later trimming)

2. Add `frontend/src/test/.../session/views/TaskToolViewStressTest.kt` modeled on
   `ReasoningViewStressTest`. It must, through the public `update(content)` API:
   - Drive hundreds of child-tool updates (append child tools 1..N, and interleave
     remove/re-add) via `Tool.childTools` snapshots.
   - `assertSame` that retained `Row` panels for unchanged child ids stay identical across
     updates (read them from the body's `Stack` component tree, not a new accessor).
   - Assert the body row `componentCount` stays bounded (equals visible child count, no
     per-update growth).
   - Assert no editor leak is trivially satisfied (rows are `JBLabel`s, no editors) — still
     capture `EditorFactory.getInstance().allEditors.size` before/after churn + a `collapse()`
     cycle to prove nothing spawns editors.
   Confirm this test passes against current `TaskToolView` before Phase 3.

### Phase 3 — Shrink test-only seams

3. Rewrite `TaskToolViewTest` assertions that currently call test-only accessors to instead
   walk the real Swing tree (helper that recurses `component.components`, as
   `ShellToolViewTest.popupScrollPanes` and `SessionMessageListPanelTest` do). Specifically
   replace usage of `rowLabels`, `bodyInsets`, `rowTitleColor(id)`, `horizontalPolicy`,
   `verticalPolicy`, `bodyScrollValue/bodyScrollBottom/setBodyScrollValue` where a tree walk
   is clean.
4. In `TaskToolView`, delete accessors that no longer have any caller after step 3; make the
   remaining test-facing ones `internal` (match `ToolView`/`ReadToolView`). Keep `labelText`,
   `bodyVisible`, `bodyMaxRows` (real internal/product callers).
5. Resolve the duplicate `rowTitleColor` name: the member accessor `rowTitleColor(id: String)`
   (`:119`) and the top-level `rowTitleColor(tool: Tool)` (`:342`) share a name for unrelated
   jobs. If the member survives step 4, rename it (e.g. `rowColor`) or fold the assertion into
   a tree walk so only the state→color helper keeps the name.

### Phase 4 — Theme-in-constructor border

6. Fix `TaskBody.panel` left inset (`TaskToolView.kt:299-304`) so the glyph-width-derived
   indent is re-evaluated on Look-and-Feel / DPI change instead of frozen at construction.
   Options: (a) override `updateUI()` on the body panel to recompute the border, or (b)
   derive the indent from a `JBUI`/style token rather than the live `glyph.preferredSize`.
   Prefer (b) if a suitable `SessionUiStyle`/`UiStyle` value exists; otherwise (a). While here,
   drop the redundant `isOpaque = true` on `TaskBody.panel` and `TaskBodyScroll` (JPanel/scroll
   default is opaque) per the "Before Returning UI Code" checklist — only if it does not change
   rendering.

### Phase 5 — SessionModel child-tool unit tests

7. Add direct model coverage in `frontend/src/test/.../session/model/` (extend the existing
   `SessionModel` test if present, else add one) for:
   - **Re-keying**: a `task` part whose `metadata["sessionId"]` changes must move tracking —
     old `childRefs`/`childTools` entry dropped, new one created (`SessionModel.kt:451-456`).
   - **Untracking on removal**: `removeMessage` / `removeContent` of a parent `task` part must
     clear its `childRefs`/`childTools` entries (`SessionModel.kt:147,159` →
     `untrackChild`), and a later `upsertChildTool` for that child becomes a no-op.
   These currently only run incidentally through `PromptLifecycleTest`.

### Phase 6 — Optional hardening (only if cheap)

8. `PromptPanel` (`:107`): consider replacing the default
   `cs: CoroutineScope = CoroutineScope(Dispatchers.Default)` with a required parameter or a
   disposable-bound scope so no uncancelled global scope is created. Production already passes
   `SessionUi`'s scope; this only affects defaults/tests. Skip if it ripples into constructors.
9. `ShellToolView.padPopup`: it scans only direct children
   (`root.components.filterIsInstance<JBScrollPane>()`). Consider the same recursive walk the
   settings-list fix uses, for robustness if the popup tree ever nests the pane deeper. Skip
   unless the popup layout changes.

## Risks

- **Trimming accessors could reduce assertion fidelity.** Mitigation: land the
  `TaskToolViewStressTest` and the tree-walk helper (Phases 2-3) *before* deleting accessors,
  so every removed accessor has an equivalent tree-based assertion first; keep the full
  `session.views` suite green after each phase.
- **`updateUI()` override ordering.** `updateUI()` runs during construction; guard against
  NPEs on fields not yet initialized (e.g. read `glyph`/style lazily or null-check) and avoid
  triggering a layout storm. Verify `test task body is indented beyond header padding`
  (`TaskToolViewTest`) still passes.
- **Dropping `isOpaque = true`** must not change the surface fill. Verify against the existing
  body-background assertions; revert that sub-step if any rendering test regresses.

## Validation

- Targeted: `./gradlew test --tests "ai.kilocode.client.session.views.TaskToolView*"`,
  `--tests "ai.kilocode.client.session.ui.SessionMessageListPanelTest"`, and
  `--tests "ai.kilocode.client.session.model.*"` from `packages/kilo-jetbrains/`.
- Full guardrails: `./gradlew typecheck test` from `packages/kilo-jetbrains/` (Java 21).
- Grep-confirm `controlCount` and any deleted accessors have zero references after removal.
- Sanity: the new stress test should fail against a deliberately broken `syncRows` (e.g.
  rebuild-all) and pass against current retained-row behavior.

## Handoff

This plan is implementation-ready. Switch to an implementation-capable agent to make the
source and test edits; do all changes in this worktree only. No `kilocode_change` markers are
required (all paths are Kilo-owned).
