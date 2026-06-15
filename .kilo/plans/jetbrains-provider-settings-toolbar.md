# JetBrains Provider Settings Toolbar

## Goal

Update JetBrains provider settings to match the model settings chrome and use standard IntelliJ toolbar actions for provider refresh and adding a custom provider.

## Current State

- `ProvidersSettingsUi` currently extends `SettingsOverlayPanel` directly and places `ProvidersContent` into `content` with `BorderLayout.CENTER`.
- `ProvidersContent` adds its own full-panel padding border, a custom text-only `JButton` refresh row, and a nested `ScrollPaneFactory.createScrollPane(list)` around the provider list.
- `ModelsSettingsUi` goes through `BaseSettingsUi` -> `SettingsPanel`, which uses one outer `JBScrollPane` with `border = null` and `HORIZONTAL_SCROLLBAR_NEVER`.
- `ProvidersConfigurable` already implements `Configurable.NoScroll`, so the provider screen is expected to provide its own scroll behavior rather than relying on the settings dialog wrapper.
- The add-custom-provider flow already exists as `ProvidersSettingsUi.custom()`, but it is not wired into the visible provider toolbar.
- IntelliJ source confirms standard add shortcuts come from `CommonShortcuts.getNewForDialogs()` / action id `NewElement`, and standard refresh shortcuts come from action id `Refresh`. Platform refresh icon is `AllIcons.Actions.Refresh`; plus/add icon is available as `AllIcons.General.Add`.

## Implementation Plan

1. Align provider settings with the model settings scroll/container pattern.
   - Change `ProvidersSettingsUi` to extend `SettingsPanel` instead of `SettingsOverlayPanel`.
   - In `init`, call `setContent(view)` instead of adding `view` directly to `content`.
   - Keep `ProvidersConfigurable : Configurable.NoScroll` unchanged so the provider settings UI remains responsible for its own scrolling.
   - Keep loading/error overlay behavior through `SettingsPanel` inheritance.

2. Remove the nested list scrollpane and list border chrome.
   - Change `ProvidersContent` to extend `BaseContentPanel` or otherwise reuse the same visual pattern used by model settings content.
   - Remove the current full-panel `border = JBUI.Borders.empty(...)` on `ProvidersContent`.
   - Remove `ScrollPaneFactory.createScrollPane(list)` and the list-specific scrollpane policy.
   - Add the provider `JBList` directly to the content area so the outer `SettingsPanel` `JBScrollPane` is the only scrollbar.
   - Preserve `ScrollingUtil.installActions(list)`, selection handling, search keyboard navigation, section headers, and row action hit testing.

3. Replace the ad hoc refresh button row with an IntelliJ action toolbar.
   - Remove the `Stack.horizontal` top row and `JButton("Refresh")`.
   - Add two local `DumbAwareAction`/`AnAction` classes or private action instances in `ProvidersSettingsUi.kt`:
     - refresh provider settings: text/description from bundle, icon `AllIcons.Actions.Refresh`, invokes `reload()`.
     - add custom provider: text/description from bundle, icon `AllIcons.General.Add`, invokes `custom()`.
   - Build a `DefaultActionGroup` with add then refresh, and create a horizontal toolbar via `ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true)`.
   - Set `toolbar.targetComponent = this` or the provider content panel so data context and shortcut handling are scoped to the settings page.
   - Put the toolbar at the top of `ProvidersContent`, alongside or just above the search field, using existing Swing layout/`Stack` patterns and without adding extra decorative borders.

4. Install standard IntelliJ shortcuts on those provider actions.
   - Add action shortcut for add using `CommonShortcuts.getNewForDialogs()` to match dialog/list add behavior (`NewElement`, typically `Alt+Insert`/mac equivalent but keymap-aware).
   - Add action shortcut for refresh using `ActionManager.getInstance().getAction("Refresh")?.shortcutSet` when available.
   - Register both shortcut sets on the provider content root via `registerCustomShortcutSet(...)` so shortcuts work when focus is in the search field or list.
   - Keep existing `Enter`, up, and down behavior for search/list navigation.

5. Update bundle strings only if needed.
   - Reuse `settings.providers.addCustom` and `settings.providers.refresh` for action text.
   - Add descriptions such as `settings.providers.addCustom.description` and `settings.providers.refresh.description` only if the action constructors/tooltips need distinct descriptions.
   - Keep all user-visible strings in `KiloBundle.properties`.

6. Update frontend tests in `ProvidersSettingsUiTest`.
   - Replace the current assertion that the north area contains a text `JButton("Refresh")`.
   - Add assertions that the provider content contains one `SearchTextField`, one `JBList<ProviderListRow>`, no nested `JScrollPane` dedicated to the list, and an action toolbar/button area with add and refresh icons/actions.
   - Add a behavior test that triggers the refresh toolbar action and verifies `reload()`/state RPC is called again, using the existing `FakeProviderRpcApi` flow.
   - Add a behavior test for the add-custom action only if it can be exercised without opening a modal dialog; otherwise validate action presence/shortcut registration and leave dialog flow unchanged.
   - Keep existing renderer, row ordering, metadata, and stale reload tests intact.

7. Verification.
   - From `packages/kilo-jetbrains/`, run the focused provider UI tests:
     - `./gradlew :frontend:test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`
   - Run JetBrains typecheck:
     - `./gradlew typecheck`
   - If tests expose brittle toolbar internals, prefer behavior assertions over production-only test accessors.

## Expected Outcome

Provider settings uses the same clean outer scrollpane and borderless settings chrome as model settings, with a standard IntelliJ toolbar containing plus/add and refresh icons. The toolbar actions use standard IntelliJ add and refresh shortcuts, and the existing provider connection/list behavior remains unchanged.

## Risks

- Removing the nested list scrollpane means the provider list height will contribute to the outer settings page height. This is intended for parity with model settings, but should be checked with large provider lists.
- Toolbar button component classes are IntelliJ implementation details, so tests should avoid depending on exact toolbar child classes where possible.
- The custom-provider dialog is modal, so automated add-action testing should avoid invoking the dialog unless there is already a safe test seam.
