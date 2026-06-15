# JetBrains Provider Settings Filterable List Plan

## Goal

Replace the JetBrains provider settings page's current row-per-provider sections with a searchable provider list that mirrors the model picker's list/section/renderer pattern. The list should filter by provider name, keep action affordances (`Connect`, `OAuth`, `Disconnect`, `Enable`) visible per row, and group rows into `Popular providers` and `All providers`.

## Current State

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt` renders providers into retained `SettingsRows` sections: `Connected providers`, `Available providers`, and `Disabled providers`.
- The file already owns the correct action callbacks and dialogs: API-key connect, OAuth authorize/callback, disconnect, enable, custom provider, and reload.
- Button visibility is already mostly correct via `buttons(provider, state, disabled)` and `configured(provider, state, ids)`:
  - disabled provider -> `Enable`
  - configured/connected provider -> `Disconnect`
  - available provider -> `Connect` and/or `OAuth`, with default API-key fallback when auth methods are absent
- The JetBrains model picker provides the list pattern to reuse:
  - `ModelPicker.kt`: `SearchTextField`, `JBList`, `CollectionListModel`, keyboard navigation, mouse activation, row syncing after filtering
  - `ModelPickerRows.kt`: filtered row construction, section title calculation
  - `ModelPickerRenderer.kt`: `GroupHeaderSeparator`, row renderer, active affordance hit testing with static helpers
- VS Code popular provider order comes from `packages/kilo-vscode/src/shared/provider-model.ts`:
  - `kilo`, `anthropic`, `deepseek`, `openai`, `google`, `openrouter`, `vercel`
- VS Code excludes Kilo Gateway from its `Popular providers` section and treats it as a separate top card. For this change, exclude `kilo` from the JetBrains popular section too, but do not add a separate Kilo card unless product asks for full VS Code parity.

## Implementation

1. Add provider list model helpers in the JetBrains provider settings package.

   Files:
   - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderCatalog.kt`
   - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRows.kt`

   Shape:
   - Define `POPULAR_PROVIDER_IDS = listOf("kilo", "anthropic", "deepseek", "openai", "google", "openrouter", "vercel")`.
   - Define `isPopularProvider(id)` and `popularProviderIndex(id)` matching VS Code ordering.
   - Add `ProviderListRow(provider, section, action)` and an action enum such as `CONNECT`, `OAUTH`, `DISCONNECT`, `ENABLE`, or `NONE` if needed.
   - Build rows from `ProviderSettingsDto` and query text:
     - filter by provider name first; include `id` as a secondary match only if it feels useful, but the user specifically requested provider-name filtering
     - exclude disabled providers from `Popular providers`; show them in `All providers` with `Enable`
     - exclude configured/connected providers from `Popular providers`; show them in `All providers` with `Disconnect`
     - exclude `kilo` from `Popular providers`
     - popular section contains unconfigured, enabled providers in `POPULAR_PROVIDER_IDS` order
     - all section contains every remaining provider sorted by name/id
   - Use the existing `ModelSearch.matches(...)` if package visibility is acceptable; otherwise add a small local `ProviderSearch.matches(query, name)` helper to avoid broadening model-picker APIs.
   - Provide `providerListSectionTitle(rows, index)` similar to `modelPickerSectionTitle`.

2. Replace `ProvidersContent` section rows with a searchable `JBList`.

   File:
   - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`

   Changes:
   - Keep the top `Add custom provider`, `Refresh`, and `status` controls.
   - Add a `SearchTextField(false)` above the list with placeholder `settings.providers.search`.
   - Use `CollectionListModel<ProviderListRow>` plus `JBList` inside a `JBScrollPane` or `ScrollPaneFactory.createScrollPane`.
   - On `update(state, error)`, store the current state, rebuild rows with the current search query, replace the model, and preserve selection where practical.
   - On search document changes, rebuild rows without reloading backend state.
   - Keep `ProvidersSettingsUi`'s existing async callbacks and `content.loading()` behavior.

3. Add a provider list renderer with button-like actions.

   File:
   - `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRenderer.kt`

   Renderer behavior:
   - Follow `ModelPickerRenderer` rather than `SettingsRow`: `JPanel(BorderLayout())`, `GroupHeaderSeparator`, `PickerRow` or equivalent row background handling, `SimpleColoredComponent`/`JBLabel` for provider name and description.
   - Show provider name as the primary text.
   - Show secondary text using the current description logic: `source · N models`.
   - Render one or more button-shaped labels/components for actions:
     - `Connect` when API-key method is available or default fallback applies
     - `OAuth` when OAuth method is available
     - `Disconnect` for configured providers, disabled when `source == "env"`
     - `Enable` for disabled providers
   - Since Swing renderers are paint-only, do not rely on actual `JButton` action listeners inside the renderer. Instead, expose hit-test helpers similar to `ModelPickerRenderer.isFavoriteClick(...)`, such as `actionAt(list, bounds, point, row)`.
   - In the `JBList` mouse listener, map the clicked row/action to the existing callbacks: `connect(provider)`, `oauth(provider)`, `disconnect(provider)`, or `enable(provider)`.
   - Add keyboard activation for the selected row. If a row has one action, Enter triggers it. If a row has multiple actions (`Connect` and `OAuth`), Enter should trigger `Connect` and keyboard users can still tab/search/select; optional follow-up can add an action popup.

4. Preserve existing provider action semantics.

   File:
   - `ProvidersSettingsUi.kt`

   Keep or move these helpers without changing behavior:
   - `description(provider)`
   - `methods(provider, state)` with API-key fallback
   - `configured(provider, state, ids)`
   - action callback methods in `ProvidersSettingsUi`

   Avoid backend/RPC changes unless implementation reveals a missing field. Current DTOs already contain providers, connected IDs, auth methods, config, disabled IDs, and source/key fields.

5. Update strings.

   File:
   - `packages/kilo-jetbrains/frontend/src/main/resources/messages/KiloBundle.properties`

   Add:
   - `settings.providers.popular=Popular providers`
   - `settings.providers.all=All providers`
   - `settings.providers.search=Filter providers`
   - `settings.providers.noMatches=No matching providers`

6. Update tests.

   File:
   - `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUiTest.kt`

   Add/adjust tests to cover:
   - available catalog provider without explicit auth still exposes `Connect`
   - provider with API and OAuth methods exposes both actions
   - configured custom provider exposes only `Disconnect`
   - popular rows use VS Code order: Anthropic, DeepSeek, OpenAI, Google, OpenRouter, Vercel
   - connected popular providers are not duplicated in `Popular providers`
   - disabled popular providers appear in `All providers` with `Enable`
   - non-popular providers appear in `All providers` alphabetically
   - filtering by provider name hides non-matching rows and section headers update correctly
   - Kilo is excluded from `Popular providers`
   - renderer/hit-test helpers map click areas to expected actions without invoking backend services

## Verification

Run from `packages/kilo-jetbrains/`:

- `./gradlew test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`
- `./gradlew typecheck`

Manual sandbox check:

- Open Settings -> Tools -> Kilo Code -> Providers.
- Type in the provider filter and confirm only provider-name matches remain.
- Confirm `Popular providers` shows the VS Code popular providers that are not connected/disabled, in VS Code order.
- Confirm `All providers` contains the rest plus connected/disabled rows with the correct actions.
- Click `Connect`, `OAuth`, `Disconnect`, and `Enable` from list rows and verify existing dialogs/flows still run.

## Risks

- Swing list renderers cannot contain live `JButton` controls. The implementation must render button-like controls and use explicit mouse hit testing, like the model picker favorite icon.
- Multiple actions per row need clear hit areas. Keep layout simple and test hit detection.
- Retained `SettingsRows` currently make action-button discovery easy in tests; tests will need to inspect the `JBList` renderer/model instead of walking actual `JButton` instances.
- Full VS Code parity for a separate Kilo Gateway row is out of scope for this filterable-list change unless requested separately.
