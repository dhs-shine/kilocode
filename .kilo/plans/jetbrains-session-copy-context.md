# JetBrains Session View Copy Context Plan

## Goal
Make right-click Copy work for JetBrains session views without installing popup handlers on every child component.

Use one session-level context-menu listener scoped to the session layered pane. At popup time, resolve the deepest component under the mouse inside the session UI, build the popup with that component's `DataContext`, and let the nearest view `UiDataProvider` provide `PlatformDataKeys.COPY_PROVIDER`.

Do not add whole-session copy behavior in this pass. Copy support is view-scoped only.

## Direction
- Keep `SessionSelection` as the single source of selected text across selectable view surfaces.
- Keep the standard IntelliJ Copy action only: `<reference ref="$Copy"/>`.
- Do not install `PopupHandler.installPopupMenu(...)` on every markdown/prose/code/prompt component.
- Install one popup dispatcher for the session root/layered pane from `SessionUi.buildUi()`.
- Make session views expose copy data with `UiDataProvider`; the popup dispatcher should resolve and use the clicked component's normal IntelliJ data context.
- Remove `SessionUi` root fallback copy provider if it is only providing copy for the entire session container. View roots and editor/text children should provide copy instead.

## Current State To Adjust
- `SessionSelection` currently owns both copy provider logic and per-component context-menu installation.
- Several components currently call `selection.installContextMenu(...)` directly.
- `SessionUi` currently implements `UiDataProvider` and delegates `COPY_PROVIDER` to `selection.provideCopy(sink)`.
- `MdViewHybrid.RootPanel`, `MdViewHybrid.CodeField`, `PromptPanel`, and `SessionEditorTextField` already expose or can expose `selection.provideCopy(sink)`.
- `PromptEditorTextField` already preserves `PromptDataKeys.SEND` through `SessionEditorTextField.uiDataSnapshot`.

## Implementation Steps
1. Split copy data from popup installation in `SessionSelection`.
   - Keep the shared `TextCopyProvider` and `provideCopy(sink)`.
   - Remove or stop using `installContextMenu(component)` for per-component popup installation.
   - If a helper remains in this file, make it install only once on the session root/layered pane, not on view children.

2. Add a session-scoped popup installer.
   - Preferred shape: a small helper such as `SessionContextMenu.install(root: JComponent, parent: Disposable)` under `session/ui/selection/` or `session/ui/`.
   - Scope it to the `SessionRootPanel`/`LayeredOverlayPanel` created in `SessionUi.buildUi()`.
   - Because normal mouse listeners on an ancestor generally do not receive mouse events targeted at descendants, use a root-scoped AWT/event-queue listener unless a quick verification confirms the layered pane receives descendant popup events.
   - Filter events to `MouseEvent` popup triggers whose source component is inside the session root and whose root is showing.
   - Handle both press and release popup triggers for cross-platform behavior.
   - Consume the event after showing the session popup to avoid duplicate native/component popups.

3. Resolve the clicked component at popup time.
   - Convert the event point from the source component into root coordinates.
   - Use Swing's deepest-component lookup from the session root coordinates, with a fallback to the source component if needed.
   - Prefer the deepest visible enabled component inside `root.content`/session UI; modal blocker content may still participate if it contains session views.
   - Build the `DataContext` from the resolved component via `DataManager.getInstance().getDataContext(target)`.

4. Build the popup from the target component's data context.
   - Look up `Kilo.Session.ContextMenu` with `ActionManager.getInstance().getAction(...) as? ActionGroup`.
   - Use a popup API that accepts an explicit `DataContext`, such as `JBPopupFactory.createActionGroupPopup(...)`, so Copy enablement is based on the clicked view/component rather than the layered-pane root.
   - Show the popup at the original mouse location, converted to the component used as the popup anchor.
   - Do not introduce a custom copy action.

5. Keep copy providers view-scoped.
   - `MdViewHybrid.RootPanel` should implement `UiDataProvider` and call `selection?.provideCopy(sink)`.
   - `MdViewHybrid.CodeField.uiDataSnapshot` must keep `super.uiDataSnapshot(sink)` first, then add `selection?.provideCopy(sink)`.
   - `PromptPanel` and prompt editor fields should expose `selection?.provideCopy(sink)` while preserving prompt-specific send data.
   - Custom question editors should receive/register the shared selection and expose the same provider through `SessionEditorTextField`.
   - Remove root/session-container fallback copy exposure from `SessionUi` if it is no longer needed for a specific focused view.

6. Remove per-component popup installs.
   - Delete calls such as `selection?.installContextMenu(...)` from markdown prose panes, code fields, fallback text areas, prompt shells/editors, scroll panes, and question custom editors.
   - Keep registration calls like `selection?.register(...)`; those are still needed for actual selection tracking.

7. Keep XML and bundle entries.
   - Keep `Kilo.Session.ContextMenu` in `frontend/src/main/resources/kilo.jetbrains.frontend.xml`.
   - The group should contain only `<reference ref="$Copy"/>`.
   - Keep `action.Kilo.Session.ContextMenu.text=Session Actions` or equivalent bundle key.

8. Update tests.
   - Keep provider tests that assert view components expose `COPY_PROVIDER` directly: markdown root, code child, prompt editor, custom editor if covered.
   - Adjust tests so `SessionUi` root is not the primary copy provider assertion.
   - Add or update a test for single installer behavior: session root installs one popup/event dispatcher, and child components do not receive per-component popup client properties/listeners from `SessionSelection`.
   - Add a focused test for target resolution if practical: given a nested view component under `SessionRootPanel`, resolving a popup point returns the nested component or its nearest useful data-provider ancestor.
   - Keep descriptor coverage asserting `Kilo.Session.ContextMenu` exists and references only `$Copy`.

9. Keep the changeset.
   - Use the existing patch changeset text or keep it equivalent: `Fix copying selected text from JetBrains session views.`

## Verification
Run from `packages/kilo-jetbrains/` after implementation:
- `./gradlew :frontend:test --tests '*SessionSelectionCopyTest' --tests '*PromptPanelTest' --tests '*MdViewHybridTest' --tests '*HistorySessionActionsTest'`
- `./gradlew typecheck`

## Notes
- This stays in the JetBrains frontend module; no backend/RPC changes are needed.
- No session transcript serialization or whole-session copy is part of this pass.
- If the explicit-data-context popup API signature differs across platform versions, inspect IntelliJ source and use the nearest public `JBPopupFactory`/Action System API that accepts a `DataContext`. Avoid internal APIs.
