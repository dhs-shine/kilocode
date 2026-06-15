# JetBrains Provider Settings Layout And Progress Plan

## Goal

Update the JetBrains provider settings UI added in the filterable-list work so it uses a `BorderLayout` root with a north toolbar and center provider list, reuses the same progress/error overlay approach as model settings, and renders row actions as standard button-style affordances with `OAuth` before `Connect`.

## Current State

- `ProvidersSettingsUi` is a `JPanel(BorderLayout())` that hosts `ProvidersContent` in the center.
- `ProvidersContent` currently extends `BaseContentPanel`, which is a vertical `Stack` intended for settings row sections.
- `ProvidersContent` currently lays out toolbar, status label, search field, and list as vertical stack children.
- Loading and error state are shown through a `JBLabel status`; this differs from model settings, where `SettingsPanel` and `SettingsProgressOverlay` provide floating progress/error messages through `showProgress`, `showError`, and `clearProgress`.
- Provider list row actions are rendered as bordered `JBLabel`s and currently use link foreground styling for enabled actions.
- `providerActions` currently returns API `CONNECT` before `OAUTH`.

## Implementation

1. Replace the provider content root layout.

   File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`

   - Change `ProvidersContent` from `BaseContentPanel`/vertical stack to a `JPanel(BorderLayout())` or `BorderLayoutPanel`.
   - Keep `ProvidersSettingsUi` as the outer disposable component unless a larger refactor becomes necessary.
   - Build a north toolbar containing only:
     - `Add custom provider`
     - `Refresh`
   - Put the provider list area in the center.
   - Keep the search field as part of the center list area, directly above the scrollable `JBList`, because the user requested north toolbar only for `Add custom` and `Refresh`.
   - Remove the inline status label from the provider content tree.

2. Reuse the model settings progress/error overlay approach.

   Files:
   - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/base/SettingsPanel.kt`
   - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`
   - Optional new helper in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/base/`

   Preferred minimal approach:
   - Extract the common progress overlay methods from `SettingsPanel` into a small reusable base such as `SettingsOverlayPanel : LayeredOverlayPanel` that owns `SettingsProgressOverlay`, registers it with the same bounds logic, and exposes `showProgress`, `showError`, and `clearProgress`.
   - Make `SettingsPanel` extend `SettingsOverlayPanel` instead of duplicating overlay ownership.
   - Make `ProvidersSettingsUi` extend `SettingsOverlayPanel` and add `ProvidersContent` to `content` using `BorderLayout.CENTER`.
   - Preserve the same overlay visual position as model settings: centered horizontally, padded from the top.
   - Avoid converting providers to `BaseSettingsUi`; providers are action-driven and do not need draft/save/modified behavior.

   Provider state handling:
   - `content.loading()` should be removed or changed into outer `showProgress(KiloBundle.message("settings.providers.loading"))` calls from `ProvidersSettingsUi`.
   - `content.error(...)` should be removed or changed into outer `showError(...)` calls from `ProvidersSettingsUi`.
   - `content.update(state, error)` should update rows only, then:
     - call `showError(error)` if the action result has an error message
     - call `showError(joined provider load errors)` if `state.errors` is not empty
     - call `clearProgress()` otherwise
   - When starting reload/connect/oauth/disconnect/enable/custom actions, call `showProgress(settings.providers.loading)` before launching the coroutine.
   - On coroutine exceptions, call `showError("${e::class.simpleName}: ${e.message}")` on EDT and leave existing list rows intact.

3. Keep center provider list behavior.

   File: `ProvidersSettingsUi.kt`

   - Retain `SearchTextField(false)`, `CollectionListModel<ProviderListRow>`, `JBList`, keyboard navigation, Enter activation, mouse hit testing, and scroll pane setup.
   - Put these in a center panel such as `BorderLayoutPanel`:
     - `NORTH`: search field
     - `CENTER`: scroll pane wrapping the list
   - Preserve selection across filtering and updates using current `sync(prefer, at)` logic.
   - Keep no-results text on the `JBList`.

4. Show `OAuth` before `Connect`.

   File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRows.kt`

   - Change `providerActions` for unconfigured providers to add `OAUTH` first, then `CONNECT`.
   - Update primary Enter activation to use the first action, so rows with both methods now default to OAuth.
   - Preserve existing behavior for disabled providers (`Enable`) and configured providers (`Disconnect`).

5. Render standard button-style actions, not link style.

   File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRenderer.kt`

   - Remove enabled link foreground usage from action labels.
   - Keep renderer actions paint-only; do not put live `JButton` instances in the renderer.
   - Make the `ActionLabel` look like a small standard button using platform-derived button colors/borders where available.
   - Use standard enabled/disabled label foregrounds rather than link colors.
   - Keep `actionAt` and `actionBounds` helpers aligned with rendered button geometry.
   - Keep disabled `Disconnect` for environment-backed providers non-clickable through `row.enabled(action)`.

6. Update tests.

   File: `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUiTest.kt`

   Add or adjust tests for:
   - `ProvidersContent` has a `BorderLayout` root.
   - The north toolbar contains `Add custom provider` and `Refresh` buttons.
   - The center area contains the provider list and search field.
   - Provider rows with API and OAuth methods return actions in `OAUTH`, `CONNECT` order.
   - Renderer action labels expose `OAuth`, `Connect` order.
   - Renderer does not use link styling for enabled actions.
   - Existing hit testing still maps each rendered button area to the correct action.
   - Existing filtering/grouping/configured/disabled behavior remains covered.

## Verification

Run from `packages/kilo-jetbrains/`:

- `./gradlew :frontend:test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`
- `./gradlew typecheck`

Manual check:

- Open Settings -> Tools -> Kilo Code -> Providers.
- Confirm only `Add custom provider` and `Refresh` are in the top toolbar.
- Confirm the search field and provider list fill the center area.
- Trigger refresh/connect/oauth/disconnect/enable flows and confirm loading/errors appear as the same floating overlay style used by model settings.
- Confirm OAuth appears before Connect and clicking each action still invokes the correct existing flow.

## Risks

- `SettingsProgressOverlay` is currently wired only by `SettingsPanel`, so extracting a shared overlay base must avoid changing model settings behavior.
- Renderer button styling must remain theme-safe and avoid live `JButton` instances in a `ListCellRenderer`.
- Changing primary action order means Enter on a row with both OAuth and API now starts OAuth; this matches the requested action order but changes keyboard default behavior.
