# JetBrains Provider Settings Fixed Toolbar And Dialog Width

## Goal

Apply the follow-up provider settings UI adjustments: keep the provider toolbar outside the scrollable settings body, and make the custom-provider dialog wider by default by giving the API key field a 50-column preferred width.

## Current State

- `ProvidersSettingsUi` extends `SettingsPanel`, whose `content` is a `BorderLayoutPanel` inherited from `LayeredOverlayPanel`.
- `SettingsPanel` currently installs one `JBScrollPane` in `content` `BorderLayout.CENTER`. Its scroll body contains `top`, a gap, and the `settings` stack.
- `ProvidersContent` currently creates the add/refresh action toolbar inside its own `BaseContentPanel` stack, so the toolbar scrolls with the search field and provider list.
- `ProvidersContent` constructor owns the toolbar action callbacks and registers add/refresh shortcuts on itself.
- `CustomProviderDialog` creates `id`, `name`, `url`, `key`, `env`, and `models` fields with default columns. The API key field is a `JBPasswordField`.

## Implementation Plan

1. Make the provider toolbar fixed above the scrollable body.
   - Move add/refresh action creation out of `ProvidersContent` and into `ProvidersSettingsUi`, or create a small private toolbar factory there.
   - Add the toolbar component to `content` with `BorderLayout.NORTH` after `SettingsPanel` has installed its scrollpane in `BorderLayout.CENTER`.
   - Keep `setContent(view)` for the scrollable search/list body so the rest remains in the center scroll area.
   - Keep `toolbar.targetComponent` scoped to the provider settings UI or provider content.

2. Keep the scrollable body top-aligned in the center.
   - Leave the existing `SettingsPanel` center scrollpane in place.
   - Ensure `ProvidersContent` only contains the search field and direct `JBList`, preserving `BaseContentPanel`/`Stack` top-to-bottom behavior.
   - Do not reintroduce a nested list `JScrollPane`.

3. Preserve standard shortcuts after moving actions.
   - Register add with `CommonShortcuts.getNewForDialogs()`.
   - Register refresh with `ActionManager.getInstance().getAction("Refresh")?.shortcutSet` when available.
   - Register shortcuts against a root component that remains present while focus is in the toolbar, search field, or list.

4. Widen the custom provider dialog through field columns.
   - Set the custom-provider API key `JBPasswordField` to `columns = 50`.
   - Prefer setting columns on the existing field over hardcoded dialog dimensions.
   - If the API key field alone does not influence the full form width because of the custom `Stack` layout, set the relevant text fields to the same columns only as needed so the dialog expands naturally.

5. Update tests.
   - Adjust `ProvidersSettingsUiTest` layout coverage to assert the toolbar is not inside `ProvidersContent` and the scrollable content still has one `SearchTextField`, one direct `JBList`, and no nested `JScrollPane`.
   - Add or update a test that inspects the `ProvidersSettingsUi.content` layout children to confirm the toolbar is in `BorderLayout.NORTH` and the scrollpane remains in `BorderLayout.CENTER`.
   - Add a dialog-width test if practical by instantiating `CustomProviderDialog` on the EDT and checking the API key/password field columns, without showing the modal dialog.

6. Verification.
   - From `packages/kilo-jetbrains/`, run `./gradlew :frontend:test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`.
   - From `packages/kilo-jetbrains/`, run `./gradlew typecheck`.

## Expected Outcome

Provider settings shows a fixed add/refresh toolbar at the top of the settings panel while the search field and provider list scroll below it. The custom-provider dialog opens wider by default because the API key field requests 50 columns.

## Risks

- `SettingsPanel.top` remains inside the scroll body, so using it for this toolbar would not satisfy the fixed-toolbar requirement; the toolbar must be added directly to `content` `BorderLayout.NORTH`.
- Toolbar component internals are IntelliJ implementation details, so tests should prefer layout-region and behavior assertions over exact toolbar child classes.
- If only the password field gets columns and the surrounding form layout does not propagate that width, other custom-provider text fields may also need matching columns to make the whole dialog consistently wider.
