# JetBrains Provider Settings Renderer Updates

## Goal
Update the JetBrains provider settings list so it matches the requested provider organization and action visibility rules:
- Add a `Connected providers` section and place configured/connected providers there.
- Hide custom provider creation/catalog rows.
- Do not allow Kilo Gateway to be removed/disconnected.
- Show action buttons only for the selected row, except connected rows show only `Disconnect` even when not selected.
- Fix row layout so trailing actions/favorite controls are right-aligned and vertically centered through `PickerRow`, and migrate the model picker favorite star onto that support.
- Add provider icons and VS Code-style provider notes where available.

## Findings
- JetBrains provider UI is centered in:
  - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRows.kt`
  - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRenderer.kt`
  - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`
- Shared picker row layout is `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/PickerRow.kt`.
- Model picker renderer currently owns its own right-side favorite star placement in `ModelPickerRenderer.kt`.
- VS Code uses `providerIcon(providerID)` in `webview-ui/src/components/settings/provider-catalog.ts`, mapping known provider ids to bundled icon names and falling back to `synthetic`.
- VS Code provider list response does not expose provider descriptions. The server schema `Provider.Info` has `id`, `name`, `source`, `env`, `key`, `options`, and `models`, but no `description`. VS Code uses hardcoded note strings for popular providers in settings.
- JetBrains already has a secondary provider line via `providerDescription(provider)`, currently `source · N models`.

## Assumptions
- "Don't show custom" means remove the visible custom provider creation path and hide unconnected custom catalog rows. Already configured/connected custom providers should still remain visible in `Connected providers` so existing user state is not orphaned.
- Kilo Gateway is an exception to the connected-row disconnect rule: it may appear as connected, but must not show or execute `Disconnect`.
- Provider descriptions should use VS Code-style popular-provider notes when known and fall back to the existing source/model-count text because the CLI does not provide a provider description field today.

## Implementation Plan
1. Add shared provider constants/metadata in `ProviderCatalog.kt`.
   - Add `KILO_PROVIDER_ID = "kilo"` and `CUSTOM_PROVIDER_PACKAGE = "@ai-sdk/openai-compatible"` to avoid repeated literals.
   - Add `providerNoteKey(id)` or `providerNote(provider)` for popular-provider descriptions matching VS Code notes for Anthropic, DeepSeek, OpenAI, Google, OpenRouter, Vercel, and Copilot-prefix providers.
   - Keep `providerDescription(provider)` as the public renderer helper, returning the localized note when present, otherwise the current source/model count.
   - Add a `providerIcon(provider)` helper returning a Swing `Icon` from bundled JetBrains resources, with Kilo and known popular/provider ids mapped first and a generic fallback.

2. Add provider icon resources minimally.
   - Bundle popular provider SVGs under `packages/kilo-jetbrains/frontend/src/main/resources/icons/providers/` where practical, copied from the existing provider icon sprite source in `packages/ui/src/components/provider-icons/sprite.svg`.
   - Use the existing `/icons/kilo.svg` for Kilo Gateway.
   - Use a platform/generic fallback icon for provider ids without a bundled resource.
   - Do not touch `packages/ui` or shared `packages/opencode` for icon support.

3. Rework provider row modeling in `ProviderListRows.kt`.
   - Add row metadata such as `connected: Boolean` and possibly `removable: Boolean`.
   - Define "connected" with existing `configured(provider, state, connectedIds)` so auth, env, config, key, and custom-config providers all group consistently.
   - Build sections in order: `Connected providers`, `Popular providers`, `All providers`.
   - Exclude connected/configured rows from popular/all sections.
   - Exclude unconnected custom rows from popular/all sections.
   - Keep disabled rows available with `ENABLE` actions unless they are custom rows hidden by the custom rule.
   - For Kilo Gateway, return no `DISCONNECT` action when connected/configured.
   - Preserve env disconnect protection by keeping `enabled(DISCONNECT) == false` for env rows or hiding the action if we choose a stricter UI guard.

4. Update provider action visibility and hit testing in `ProviderListRenderer.kt`.
   - Add a single `visibleActions(row, selected)` helper.
   - If `row.connected` and row can be disconnected, return only `DISCONNECT` regardless of selection.
   - If the row is not connected, return `row.actions` only when selected.
   - If the row is Kilo Gateway connected, return no actions.
   - Use `visibleActions` for rendering, `actionBounds`, and `actionAt` so hidden actions are not clickable.
   - Render provider icon to the left of provider name, then title/description text, then trailing actions.
   - Keep action labels styled as lightweight buttons using platform colors.

5. Extend `PickerRow.kt` for trailing controls.
   - Add a compatible `setContent(content: JComponent, trailing: JComponent? = null, border: Border? = null)` or equivalent API.
   - Internally place content in center and optional trailing component at the right, vertically centered using the existing `Align` layout helper.
   - Keep existing `setContent(component)` behavior for mode picker and any other caller.
   - Avoid hardcoded raw Swing dimensions/colors; use `UiStyle.Gap`, `JBUI`, and platform colors.

6. Migrate renderers to the new `PickerRow` trailing support.
   - Provider renderer: pass its action panel as `PickerRow` trailing content instead of using `FlowLayout` in `BorderLayout.EAST` inside the renderer row.
   - Model picker renderer: move the favorite star out of the internal `row.add(star, BorderLayout.EAST)` and pass it as `PickerRow` trailing content.
   - Keep model picker favorite visibility behavior and click hit testing unchanged from the user's perspective.
   - Leave mode picker on the backward-compatible `setContent(row)` path unless a trivial migration is needed.

7. Remove visible custom-provider entry points in `ProvidersSettingsUi.kt`.
   - Remove the `Add custom provider` toolbar button from `ProvidersContent`.
   - Keep backend/custom dialog code in place unless it becomes unused enough to fail lint/typecheck; this minimizes behavioral churn and preserves future reuse.
   - Update constructor/test helpers for the removed custom callback if needed.

8. Add backend Kilo Gateway disconnect guard in `KiloBackendProviderSettingsManager.kt`.
   - Change `disconnect(providerId = "kilo")` from logout/profile clearing to returning the current state with a user-facing error such as `Kilo Gateway cannot be disconnected from provider settings.`
   - Keep profile logout available through the profile flow, not provider settings.

9. Update localized strings in `KiloBundle.properties`.
   - Add provider note strings mirroring VS Code's English notes.
   - Add the Kilo Gateway disconnect guard error string if surfaced from backend/frontend.
   - Remove or stop using `settings.providers.addCustom` in this UI path.

10. Add/update tests.
   - `ProvidersSettingsUiTest`:
     - connected/configured providers appear under `Connected providers` first.
     - connected rows are not duplicated in popular/all.
     - unconnected custom rows and the add-custom button are hidden.
     - connected rows render only `Disconnect` when not selected.
     - unselected unconnected rows render no actions and hit testing returns null.
     - selected unconnected rows render their connect/oauth/enable actions.
     - Kilo Gateway connected rows render no disconnect and cannot activate disconnect.
     - provider renderer exposes icon + description/note text as expected.
     - action label bounds are vertically centered in row bounds.
   - `ModelPickerTest`:
     - favorite star behavior and click hit testing remain unchanged after moving star to `PickerRow` trailing support.
   - `KiloBackendProviderSettingsManagerTest`:
     - disconnecting `kilo` returns an error/no-op result and does not call logout/auth removal.
   - Parser/DTO tests only if provider DTO/icon metadata requires DTO changes; current plan avoids DTO changes.

11. Add a changeset.
   - Create a new `.changeset/*.md` because this is user-facing JetBrains provider settings behavior.
   - Patch entry should describe the user-visible provider settings list/action changes.

## Verification
Run the smallest relevant checks from `packages/kilo-jetbrains/`:
- `./gradlew :frontend:test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`
- `./gradlew :frontend:test --tests ai.kilocode.client.session.ui.model.ModelPickerTest`
- `./gradlew :backend:test --tests ai.kilocode.backend.provider.KiloBackendProviderSettingsManagerTest`
- `./gradlew typecheck`

## Risks
- Bundling many provider brand icons could create a noisy diff. Start with Kilo/popular icons plus a fallback, then expand only if needed.
- If "don't show custom" is intended to hide already-connected custom providers too, the row filtering rule will need one small adjustment.
- `PickerRow` is shared by provider, model, and mode pickers; keep its existing single-content API backward-compatible and verify model picker tests after migration.
