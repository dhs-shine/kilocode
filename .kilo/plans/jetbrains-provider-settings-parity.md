# JetBrains Provider Settings Parity Plan

## Goal

Bring the JetBrains plugin's provider settings experience to parity with the VS Code extension's Providers tab while reusing the existing JetBrains split-mode settings architecture and model picker infrastructure.

Assumption: "parity" targets provider management from VS Code settings: provider catalog, connect/disconnect, OAuth/API-key auth, disabled providers, and OpenAI-compatible custom providers. Autocomplete provider/model settings are a separate optional follow-up because JetBrains currently only stores autocomplete booleans.

## Current State

### VS Code Provider Settings

VS Code implements provider settings across:

- `packages/kilo-vscode/webview-ui/src/components/settings/ProvidersTab.tsx`
- `packages/kilo-vscode/webview-ui/src/components/settings/ProviderConnectDialog.tsx`
- `packages/kilo-vscode/webview-ui/src/components/settings/CustomProviderDialog.tsx`
- `packages/kilo-vscode/src/provider-actions.ts`
- `packages/kilo-vscode/src/shared/custom-provider.ts`
- `packages/kilo-vscode/src/shared/fetch-models.ts`

Capabilities to mirror:

- Provider catalog from `GET /provider?directory=...`, grouped as Kilo Gateway, connected, popular/all, custom, and disabled.
- Provider auth methods from `GET /provider/auth?directory=...`.
- API-key connect through `PUT /auth/:providerID` with optional prompt metadata.
- OAuth connect through `/provider/:providerID/oauth/authorize` and `/provider/:providerID/oauth/callback`.
- Disconnect through `DELETE /auth/:providerID`, plus `disabled_providers` updates for config-sourced built-in providers.
- Custom OpenAI-compatible providers persisted to global config under `provider[id]`, with API keys in auth storage unless `{env:VAR}` is used.
- Custom model fetching from `<baseURL>/models` with optional API key and headers.
- Removed custom providers/models/variants represented by `null` sentinels because config updates are deep-merged.
- Kilo Gateway remains special: login/logout uses profile/device-auth UI rather than generic API-key provider setup.

### JetBrains Model Settings

JetBrains already has a model settings page:

- `packages/kilo-jetbrains/frontend/src/main/resources/kilo.jetbrains.frontend.xml` registers `ModelsConfigurable` under Tools -> Kilo Code.
- `ModelsConfigurable.kt` creates `ModelsSettingsUi` with the first open project directory.
- `ModelsSettingsUi.kt` extends `BaseSettingsUi`, loads app state from `KiloAppService`, loads workspace models via `KiloWorkspaceService.models(directory)`, and renders default/small/subagent/per-mode model pickers.
- `ModelsSettingsState.kt` maps `ConfigDto` to a `ModelsDraft` and builds `ConfigPatchDto` for `model`, `small_model`, `subagent_model`, `subagent_variant`, and `agent.<mode>.model`.
- `KiloAppService.updateConfigAsync()` calls `KiloAppRpcApi.updateConfig()`.
- `KiloBackendAppService.updateConfig()` sends a raw `PATCH /global/config` using `KiloCliDataParser.buildConfigPatch()`.
- `KiloWorkspaceRpcApi.models(directory)` calls `GET /provider?directory=...` and `/agent`, then maps results through `KiloWorkspaceDtoMapper`.

Current JetBrains gaps:

- No Providers settings page/configurable.
- No shared DTOs for auth methods, OAuth authorization, auth prompts, provider config, disabled providers, or custom provider forms.
- `ProviderDto` only carries `id`, `name`, `source`, and models; it does not expose source auth state, keys, or config details needed for settings.
- `ConfigDto` only carries model fields and agent config; it does not expose `provider`, `disabled_providers`, or `enabled_providers`.
- `KiloCliDataParser.buildConfigPatch()` only supports model keys; provider config updates need a separate patch path.
- Generic provider auth actions do not exist in JetBrains; only Kilo Gateway login/logout is wired through profile settings.
- `ModelsSettingsUi` filters models to Kilo plus connected providers, so disconnected providers cannot be configured from model settings.

## Proposed Architecture

Add a provider-settings feature parallel to the existing Models settings feature.

### Shared RPC

Add a dedicated provider RPC rather than overloading model/app RPC:

- New `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/KiloProviderRpcApi.kt`.
- New DTOs under `shared/src/main/kotlin/ai/kilocode/rpc/dto/ProviderSettingsDto.kt`.
- Register a new backend provider in `backend/src/main/resources/kilo.jetbrains.backend.xml`.

Suggested RPC methods:

- `suspend fun state(directory: String): ProviderSettingsDto`
- `suspend fun connect(input: ProviderConnectDto): ProviderActionResultDto`
- `suspend fun authorize(input: ProviderOAuthAuthorizeDto): ProviderOAuthReadyDto`
- `suspend fun callback(input: ProviderOAuthCallbackDto): ProviderActionResultDto`
- `suspend fun disconnect(input: ProviderDisconnectDto): ProviderActionResultDto`
- `suspend fun saveCustom(input: CustomProviderSaveDto): ProviderActionResultDto`
- `suspend fun fetchCustomModels(input: CustomModelFetchDto): CustomModelFetchResultDto`

Key DTOs:

- `ProviderSettingsDto`: providers, connected IDs, defaults, auth methods, auth states, global provider config, merged provider config, disabled providers, enabled providers, errors.
- `ProviderAuthMethodDto`: type `api|oauth`, label, prompts.
- `ProviderAuthPromptDto`: text/select prompt data plus optional `when` condition.
- `ProviderAuthorizationDto`: URL, method `auto|code`, instructions.
- `CustomProviderConfigDto`: id/name/npm/env/options/models/headers/variants.
- `ProviderActionResultDto`: refreshed `ProviderSettingsDto`, optional profile-cleared flag, optional user-facing error.

Use serializable DTOs, not frontend/backend platform types. Prefer typed DTOs for known fields, with `kotlinx.serialization.json.JsonObject` only for variant config values if a fully typed shape becomes too noisy.

### Backend

Add a backend manager focused on provider settings:

- `backend/src/main/kotlin/ai/kilocode/backend/provider/KiloBackendProviderSettingsManager.kt`
- `backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloProviderRpcApiImpl.kt`
- `backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloProviderRpcApiProvider.kt`

Responsibilities:

- Require app readiness and use `app.http`/`app.port` for raw HTTP where generated API coverage is missing.
- Fetch provider state with:
  - `GET /provider?directory=...`
  - `GET /provider/auth?directory=...`
  - `GET /global/config`
  - `GET /config?directory=...`
- Connect API-key providers with `PUT /auth/:providerID`, payload `{ type: "api", key, metadata? }`.
- Start/complete OAuth with provider auth endpoints and prompt input support.
- Disconnect providers using VS Code's semantics:
  - Env-sourced providers are not disconnectable in UI.
  - `DELETE /auth/:providerID` for auth-backed providers.
  - For config-sourced built-ins, add provider ID to global `disabled_providers` instead of deleting config.
  - For custom providers, write `provider[id] = null` globally and locally if needed.
  - For Kilo, clear profile state or call existing logout/profile refresh flow as appropriate.
- Save custom providers globally:
  - Validate/sanitize provider ID, base URL, env var, headers, models, variants.
  - Write config under `provider[id]` with `npm = "@ai-sdk/openai-compatible"`.
  - Remove ID from `disabled_providers`.
  - Store, preserve, or clear auth based on API-key change mode.
  - Add null sentinels for removed models/variants.
- Fetch custom provider models directly from `<baseURL>/models` using OkHttp with 15s timeout, optional bearer token, and custom headers.
- After auth/config changes, call `POST /global/dispose` or the existing generated `global.dispose` equivalent if available, then return refreshed provider state. If no generated client method exists, use raw HTTP.

Add pure helper/parsing coverage in `KiloCliDataParser` or a new provider-specific parser:

- Parse provider auth method payloads.
- Parse relevant config provider maps and disabled/enabled provider lists.
- Build provider config patches without weakening the existing model-only `ConfigPatchDto` path.
- Build auth/OAuth request JSON safely.
- Build custom provider patches with deletion/null sentinels.

### Frontend Service

Add a frontend service mirroring `KiloWorkspaceService`/`KiloAppService` patterns:

- `frontend/src/main/kotlin/ai/kilocode/client/app/KiloProviderService.kt`

Responsibilities:

- Resolve `KiloProviderRpcApi` via `durable {}`.
- Expose suspend wrappers for load/connect/authorize/callback/disconnect/saveCustom/fetchCustomModels.
- Log failures and return typed error DTOs where possible.
- Trigger `KiloWorkspaceService.reload(directory)` and `KiloAppService.refreshProfileAsync()` after actions that can affect model availability/profile state.

### Frontend Settings UI

Add a Providers configurable parallel to Models:

- `frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersConfigurable.kt`
- `ProvidersSettingsUi.kt`
- `ProvidersSettingsState.kt`
- Provider list/card/dialog components in the same package.
- Register it in `kilo.jetbrains.frontend.xml` under `ai.kilocode.jetbrains.settings`.
- Add a link from `KiloSettingsConfigurable.kt`.
- Add strings to `KiloBundle.properties`.

UI shape:

- Use standard Swing/IntelliJ components only; no UI DSL, Compose, or JCEF.
- Follow current settings patterns: `BaseSettingsUi`, `BaseContentPanel`, `SettingsRows`, `SettingsBannerKind`, `Stack`, `UiStyle`.
- Render progressively: loading state first, then provider cards after backend data arrives.
- Sections:
  - Kilo Gateway account card with sign-in/profile link.
  - Connected providers.
  - Popular providers and/or all providers picker.
  - Custom providers.
  - Disabled providers with re-enable action.
- Provider card data:
  - name, source tag, connection state, model count, actions.
  - source handling for `env`, `api`, `config`, `custom`, OAuth/custom, and Kilo Gateway.
- API-key connect dialog:
  - password field for key.
  - backend-driven text/select prompts with `when` conditions.
  - optional-key behavior for local providers like `atomic-chat` and `lmstudio`.
  - provider-specific labels/placeholders for Azure prompts.
- OAuth dialog:
  - choose OAuth method if multiple exist.
  - open URL in browser.
  - support `code` method with code entry.
  - support `auto` method with waiting/instructions UI.
- Custom provider dialog:
  - provider ID/name/base URL/API key or `{env:VAR}`.
  - headers list with duplicate validation.
  - model list with add/remove/edit.
  - fetch models from `/models`, select all/deselect all, and avoid duplicates.
  - reasoning flag and variant rows with the same fields VS Code supports.
  - edit existing custom providers and preserve masked/unchanged secrets.

### Model Settings Integration

After provider actions, existing model settings should immediately reflect new availability:

- Reload workspace provider data after connect/disconnect/custom save.
- Keep `ModelsSettingsUi.items()` filtering as Kilo plus connected providers.
- If a configured default model becomes unavailable because its provider was disabled, show the existing no/invalid provider messaging where possible; add a targeted warning only if current UI silently looks valid.
- No changes to prompt-level model override storage are required.

## Implementation Phases

### Phase 1: Backend Contract and Data Loading

1. Add provider settings DTOs and `KiloProviderRpcApi` in `shared`.
2. Add backend RPC provider registration.
3. Implement `state(directory)` using raw HTTP for `/provider`, `/provider/auth`, `/global/config`, and `/config`.
4. Extend/add parser tests for provider settings payloads.
5. Add frontend `KiloProviderService` with a simple load method.

Deliverable: JetBrains frontend can load the same provider/auth/config data VS Code uses, but no UI actions yet.

### Phase 2: Providers Settings Page Skeleton

1. Add `ProvidersConfigurable` and register it in settings XML.
2. Add root settings link and localized strings.
3. Implement loading/error/empty states and provider sections.
4. Show connected, disabled, Kilo, custom, and available provider cards with non-functional or minimally functional actions.
5. Add tests for settings registration/state rendering where existing settings tests make this practical.

Deliverable: Users can inspect providers in JetBrains settings.

### Phase 3: Built-in Provider Connect/Disconnect

1. Implement backend API-key connect, OAuth authorize/callback, and disconnect flows.
2. Port VS Code provider ID validation and metadata cleanup semantics to Kotlin.
3. Implement frontend connect dialogs for API key and OAuth.
4. Implement disabled provider re-enable.
5. Refresh provider/model state after successful actions.
6. Add backend tests using `MockCliServer` for request bodies and config patches.
7. Add frontend UI tests for dialog validation, prompt visibility, and action dispatch.

Deliverable: Users can connect/disconnect built-in providers with API-key or OAuth auth.

### Phase 4: Custom Provider Parity

1. Implement custom provider validation/sanitization helpers in shared/backend Kotlin.
2. Implement backend custom save/delete with global config patches, auth set/remove/preserve, and null deletion sentinels.
3. Implement backend custom `/models` fetch with auth/header support.
4. Build custom provider Swing dialog with model/header/variant editing.
5. Add tests for validation, sentinel patching, auth change modes, and model fetch error handling.

Deliverable: Users can create, edit, delete, and fetch models for OpenAI-compatible custom providers.

### Phase 5: Polish and Optional Parity

1. Add provider search/all-provider popup if the initial page is too long.
2. Add provider icons if reusable IntelliJ-safe assets are available.
3. Add telemetry events for provider settings actions if product wants parity with VS Code analytics.
4. Decide whether to add autocomplete provider/model parity. This likely needs a separate JetBrains settings page because current JetBrains autocomplete settings only stores booleans in `KiloAutocompleteSettingsService`.
5. Consider local config editing parity. VS Code reads merged/global config and deletes local custom providers when necessary; JetBrains can start global-only but should support local deletion if custom providers can be workspace-scoped.

## Test Plan

Run from `packages/kilo-jetbrains/`:

- `./gradlew typecheck`
- Targeted backend tests for provider parsing/actions, e.g. `./gradlew backend:test --tests '*Provider*'` if Gradle test filtering supports it.
- Targeted frontend tests for provider settings UI/dialogs, e.g. `./gradlew frontend:test --tests '*Provider*'` if available.
- Full `./gradlew test` before merging the completed feature.

Manual verification in sandbox:

- Open Settings -> Tools -> Kilo Code -> Providers.
- Connect an API-key provider and verify it appears in model pickers.
- Disconnect a config-sourced provider and verify it moves to disabled providers.
- Connect OAuth provider with both `auto` and `code` styles if available.
- Add an OpenAI-compatible custom provider, fetch models, select a model, then use it in chat.
- Edit custom provider to remove a model/variant and confirm it is removed from config after refresh.
- Confirm Kilo Gateway login/logout still works through User Profile.

## Risks and Mitigations

- Provider settings are large. Ship in phases so loading/catalog and built-in auth can land before custom provider editing.
- Generated JetBrains API may not expose every CLI route. Use raw OkHttp requests in backend, matching current `KiloWorkspaceRpcApiImpl.models()` and `KiloBackendAppService.updateConfig()` patterns.
- RPC DTOs can get complex. Keep frontend/backend contracts typed for UI-critical fields and use focused parser/helper tests.
- Config patch semantics are deep-merge based. Preserve VS Code's null-sentinel behavior for provider/model/variant deletion.
- Split-mode constraints require all backend work to stay backend-side and all Swing work frontend-side; use RPC only from frontend coroutines, never the EDT.
- Env-sourced providers should not offer destructive disconnect actions; explain that they are configured through the environment.
