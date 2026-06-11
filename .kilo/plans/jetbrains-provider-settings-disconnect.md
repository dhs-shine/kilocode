# Fix JetBrains Provider Settings Connect Actions And Debug Logs

## Goal

Fix the JetBrains provider settings screen so every available provider has an appropriate action button, keep only actually configured providers disconnectable, and make provider-settings backend diagnostics DEBUG-level with one debug status line per loaded provider.

## Findings

- The screenshot shows many rows in `Available providers` with no action button. In `ProvidersSettingsUi.kt`, `buttons()` only renders `Connect` when `state.auth[provider.id]` contains an `api` method and only renders `OAuth` when it contains an `oauth` method. Catalog providers from models.dev often have no dedicated auth hook, so `state.auth[provider.id]` is empty and the row renders blank.
- The CLI TUI handles the same case by falling back to one default auth method when `provider_auth[providerID]` is absent: `{ type: "api", label: "API key" }` in `dialog-provider.tsx`.
- Catalog providers from models.dev can have `source: "custom"` because `Provider.fromModelsDevProvider()` sets that value for openai-compatible catalog entries. This does not mean they are user-configured or connected.
- `ProvidersSettingsUi.kt` already has a `configured()` predicate that excludes bare `source == "custom"`; keep using that for section placement and disconnect rendering.
- `KiloBackendProviderSettingsManager.kt` currently logs high-volume state/load/http messages at `info`. The user wants this class to log at `DEBUG`, and also wants logging for every loaded provider's status.

## Implementation Plan

1. Fix available-provider buttons in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`.
   - Add a small helper for auth methods, e.g. `methods(provider, state)`, that returns `state.auth[provider.id]` when present and otherwise returns a default `ProviderAuthMethodDto(type = "api", label = "API key")` for available providers.
   - Use this helper from `buttons()` instead of `state.auth[provider.id].orEmpty()`.
   - Preserve existing behavior for providers with explicit methods: show `Connect` for `api`, `OAuth` for `oauth`, and both when both are present.
   - Do not show fallback `Connect` for disabled providers or already configured/connected providers, because those paths already return `Enable` or `Disconnect`.
   - Keep `configured()` unchanged in meaning: connected/configured when the provider id is in `state.connected`, `provider.key != null`, `provider.source == "config"`, or the id exists in `state.config`. Do not treat bare `source == "custom"` as connected.

2. Keep backend disconnect protection in `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/provider/KiloBackendProviderSettingsManager.kt`.
   - Preserve the guard that returns `ProviderActionResultDto(current, error = "Provider is not connected.")` when the target provider is not actually connected/configured.
   - Keep special handling for `kilo` logout and env-backed providers.
   - Keep OpenAI-compatible custom config deletion only for providers present in `current.config` with `npm == "@ai-sdk/openai-compatible"`.

3. Move provider-settings backend diagnostic logs to DEBUG in `KiloBackendProviderSettingsManager.kt`.
   - Change state start/completed, resource load start/completed, HTTP start/completed, and expected HTTP failure diagnostics from `LOG.info(...)` to `LOG.debug { ... }`.
   - Keep warnings for actual load/request failures where the UI needs error context (`LOG.warn(...)`) unless they are intentionally non-actionable, such as dispose cleanup already being debug-only.
   - Avoid logging secrets: do not log auth bodies, API keys, headers, or full URLs with query strings. Existing path-only logging via `request.url.encodedPath` is acceptable.

4. Add per-provider DEBUG status logging in `KiloBackendProviderSettingsManager.state()`.
   - After building `result`, iterate over `result.providers` and log one DEBUG line per provider with non-secret status fields.
   - Include: `id`, `source`, `connected` boolean, `configured` boolean, `disabled` boolean, `enabled` boolean, `hasKey` boolean, `auth` method types, `config` boolean, and model count.
   - Use the same connected/configured semantics as frontend/backend disconnect guard: `provider.id in result.connected || provider.key != null || provider.source == "config" || provider.id in result.config`.
   - This directly exposes why rows appear under Connected, Available, or Disabled and why a row has Connect/OAuth/Disconnect/Enable.

5. Update targeted frontend tests in `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUiTest.kt`.
   - Add/adjust a test for an available catalog provider with no `auth` entry; assert it renders `Connect` and not `Disconnect`.
   - Keep the existing test for a catalog custom provider with explicit `api` and `oauth` methods; assert it renders `Connect` and `OAuth`, not `Disconnect`.
   - Keep the configured custom provider test; assert it renders only `Disconnect` for auth action buttons.

6. Add/adjust backend tests in `packages/kilo-jetbrains/backend/src/test/kotlin/ai/kilocode/backend/provider/KiloBackendProviderSettingsManagerTest.kt` only if needed.
   - Existing disconnect guard tests already cover the stale disconnect behavior.
   - Do not add brittle assertions for DEBUG log output unless there is an existing test log sink that makes this straightforward without coupling tests to exact log text.

7. Verification.
   - Run targeted JetBrains tests from `packages/kilo-jetbrains/`:
     - `./gradlew test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest --tests ai.kilocode.backend.provider.KiloBackendProviderSettingsManagerTest`
   - Run `./gradlew typecheck` from `packages/kilo-jetbrains/`.
   - If Java 21 or Gradle setup blocks verification, report the exact blocker.

## Expected Outcome

- Available catalog providers without explicit `/provider/auth` methods show `Connect` using the same default API-key fallback as the CLI TUI.
- Available providers with explicit auth methods show the correct `Connect` and/or `OAuth` buttons.
- Only actually connected/configured providers show `Disconnect`.
- Backend provider-settings logs are no longer noisy at INFO, and enabling DEBUG shows one status line for every loaded provider without exposing secrets.

## Out Of Scope

- No CLI/opencode changes are planned for this fix.
- No SDK regeneration is needed.
- No changes to the custom-provider creation flow are needed.
