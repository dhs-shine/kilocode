# JetBrains Provider Metadata And Icons

## Goal

Make VS Code and JetBrains use the same provider display metadata for the Providers settings UI:

- Provider display name from the CLI provider catalog.
- Provider description/note from a shared metadata contract.
- Provider icon ID from a shared metadata contract, with each client rendering the icon using its native UI stack.

The shared source of truth should be the CLI `/provider` response. VS Code and JetBrains should not maintain separate provider-description switch statements.

## Current State

- VS Code provider settings render provider names from the provider list response.
- VS Code provider icons and notes are client-side in `packages/kilo-vscode/webview-ui/src/components/settings/provider-catalog.ts`.
- VS Code provider note strings are mostly in `packages/kilo-vscode/webview-ui/src/i18n/*`; `dialog.provider.kilo.note` already exists in `packages/kilo-i18n/src/*` and is merged into VS Code translations.
- Provider SVG source assets live in `packages/ui/src/assets/icons/provider/*.svg` and are compiled into the web sprite used by `@kilocode/kilo-ui/provider-icon`.
- JetBrains provider settings parse `/provider` in `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/cli/KiloCliDataParser.kt` and pass data through `ProviderSettingsProviderDto`.
- JetBrains provider descriptions and icons are currently hardcoded in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderCatalog.kt`.

## Architecture

Use this ownership split:

- CLI owns provider display metadata: `noteKey`, English fallback `note`, and `icon` ID.
- `packages/kilo-i18n` owns shared localized note strings for clients that can translate `noteKey`.
- `packages/ui/src/assets/icons/provider` remains the icon asset source.
- VS Code keeps rendering with `ProviderIcon` and localizes `noteKey` through the webview i18n context.
- JetBrains copies the same SVG assets into generated plugin resources and renders them with `IconLoader`.
- JetBrains initially localizes only if a matching bundle key exists; otherwise it falls back to CLI `metadata.note`.

This avoids extracting UI code across clients while still sharing the data contract.

## Data Contract

Add optional metadata to provider list items:

```ts
metadata?: {
  noteKey?: string
  note?: string
  icon?: string
}
```

Semantics:

- `noteKey`: stable translation key, e.g. `dialog.provider.openai.note`.
- `note`: English fallback text for clients without that translation key.
- `icon`: provider icon ID matching `packages/ui/src/assets/icons/provider/<icon>.svg`; fallback is `synthetic`.

## Implementation Steps

### 1. Add Kilo-Owned CLI Metadata Helper

Create `packages/opencode/src/kilocode/provider/metadata.ts`.

Responsibilities:

- Define the metadata shape with `noteKey`, `note`, and `icon`.
- Export a helper such as `providerMetadata(providerID: string)`.
- Include current popular providers:
  - `kilo`
  - `opencode`
  - `anthropic`
  - `deepseek`
  - `github-copilot*` mapped to icon `github-copilot`
  - `openai`
  - `google`
  - `openrouter`
  - `vercel`
- Return `icon: providerID` when the icon exists, with explicit exceptions for aliases and fallback `synthetic`.
- Keep the English fallback strings here so JetBrains can show the same descriptions before full Kotlin resource generation exists.

Keep this file under `kilocode` so no `kilocode_change` markers are needed.

### 2. Expose Metadata On `/provider`

Touch shared upstream files only where required, with narrow `kilocode_change` markers.

Likely files:

- `packages/opencode/src/provider/provider.ts`
  - Add optional `metadata` to `Provider.Info` schema.
  - Keep this as a small schema-only change.
- `packages/opencode/src/server/routes/instance/httpapi/handlers/provider.ts`
  - Import the Kilo metadata helper.
  - When building the public `all` array for `/provider`, attach `metadata` to each public provider item.

Prefer not to put Kilo metadata inside `Provider.toPublicInfo()` unless necessary. Applying metadata at the HTTP boundary keeps the shared provider core closer to upstream and avoids affecting internal provider/model setup paths.

Run the annotation checker after implementation:

```bash
bun run script/check-opencode-annotations.ts
```

### 3. Regenerate SDK/OpenAPI

After changing the HTTP schema, run from repo root:

```bash
./script/generate.ts
```

Expected generated outputs include SDK/OpenAPI type changes under `packages/sdk/`, especially provider list response types. Do not hand-edit generated files.

### 4. Move Shared Provider Notes Into `packages/kilo-i18n`

Add provider note keys to `packages/kilo-i18n/src/*.ts`:

- `dialog.provider.opencode.note`
- `dialog.provider.anthropic.note`
- `dialog.provider.deepseek.note`
- `dialog.provider.copilot.note`
- `dialog.provider.openai.note`
- `dialog.provider.google.note`
- `dialog.provider.openrouter.note`
- `dialog.provider.vercel.note`

`dialog.provider.kilo.note` already exists there.

Then remove duplicate provider-note strings from `packages/kilo-vscode/webview-ui/src/i18n/*` where they are now supplied by `packages/kilo-i18n`.

VS Code merges `packages/kilo-i18n` after app/UI dictionaries in `packages/kilo-vscode/webview-ui/src/context/language.tsx`, so existing `language.t(noteKey)` lookups continue to work.

### 5. Update VS Code To Consume Metadata

Files:

- `packages/kilo-vscode/webview-ui/src/types/messages/providers.ts`
  - Add optional `metadata` to the webview provider type.
- `packages/kilo-vscode/webview-ui/src/components/settings/provider-catalog.ts`
  - Replace ID-only icon/note helpers with metadata-aware helpers.
  - Prefer `provider.metadata.icon` when valid, then provider ID, then `synthetic`.
  - Prefer `provider.metadata.noteKey`, then `provider.metadata.note`, then old local fallback if needed for compatibility.
- `packages/kilo-vscode/webview-ui/src/components/settings/ProvidersTab.tsx`
  - Use metadata-aware description rendering for popular provider notes.
  - Continue translating note keys with `language.t`.
- `packages/kilo-vscode/webview-ui/src/components/settings/ProviderSelectDialog.tsx`
  - Use metadata-aware icon lookup if the provider item carries metadata; otherwise keep the ID fallback.

Keep a small compatibility fallback during rollout so older CLI/provider responses without metadata still render correctly.

### 6. Add Metadata DTOs To JetBrains Shared RPC

File:

- `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/ProviderSettingsDto.kt`

Add a serializable nested DTO, for example:

```kotlin
@Serializable
data class ProviderMetadataDto(
    val noteKey: String? = null,
    val note: String? = null,
    val icon: String? = null,
)
```

Add `metadata: ProviderMetadataDto? = null` to `ProviderSettingsProviderDto`.

### 7. Parse Metadata In JetBrains Backend

File:

- `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/cli/KiloCliDataParser.kt`

Update `parseProviderSettingsProviders()` to parse `item["metadata"]` into `ProviderMetadataDto`.

Keep unknown-field tolerance. If metadata is missing or malformed, preserve current behavior by using `null` metadata and generic fallbacks.

### 8. Generate JetBrains Provider Icon Resources

File:

- `packages/kilo-jetbrains/frontend/build.gradle.kts`

Add a Gradle task that copies provider SVGs from:

```text
../../ui/src/assets/icons/provider/*.svg
```

Into generated resources, for example:

```text
frontend/build/generated/provider-icons/icons/providers/
```

Add that generated directory to `sourceSets.main.resources`, and make `processResources` depend on the copy task.

Normalize SVGs that use `currentColor` because IntelliJ SVG loading does not theme inherited `currentColor` reliably:

- Light variant: replace `currentColor` with a neutral icon color such as `#6E6E6E`.
- Dark variant: emit `<name>_dark.svg` with a neutral dark icon color such as `#CED0D6`.

Generated SVG resources should not be committed.

### 9. Update JetBrains Provider Rendering

File:

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderCatalog.kt`

Change `providerDescription(provider)`:

- If `provider.metadata?.noteKey` exists and `KiloBundle` can resolve it, use the localized bundle message.
- Otherwise if `provider.metadata?.note` exists, use it.
- Otherwise keep the generic fallback: `source · N models`.

Change `providerIcon(provider)`:

- Use `provider.metadata?.icon ?: provider.id`.
- Load `/icons/providers/<icon>.svg` with `IconLoader`.
- Fallback to `/icons/providers/synthetic.svg`.
- Final fallback to `AllIcons.Nodes.Plugin` if the generated resource is missing.
- Cache icons by icon ID to avoid repeated `IconLoader` work during list rendering.

Remove the hardcoded `providerNoteKey()` switch once metadata is the source of truth.

### 10. Optional JetBrains Localization Generation

If full localized JetBrains descriptions are required in this change, add a generation step that creates JetBrains resource bundle entries from `packages/kilo-i18n/src/*.ts`.

Suggested approach:

- Generate `.properties` files under `packages/kilo-jetbrains/frontend/build/generated/i18n/`.
- Include generated resources in `sourceSets.main.resources`.
- Use the existing `KiloBundle` lookup path.
- Fall back to CLI `metadata.note` for missing keys.

If this is deferred, JetBrains still displays the same English descriptions via `metadata.note`, while VS Code remains localized through `metadata.noteKey`.

### 11. Tests

CLI:

- Add a Kilo-owned test under `packages/opencode/test/kilocode/` for `providerMetadata()` or `/provider` metadata shape.
- Prefer testing the helper directly if starting a full provider list test is heavier than needed.

VS Code:

- Update/add unit coverage for metadata-aware icon and description helpers if an existing test location fits.
- At minimum rely on `bun run typecheck` for provider type propagation.

JetBrains backend:

- Update `packages/kilo-jetbrains/backend/src/test/kotlin/ai/kilocode/backend/cli/KiloCliDataParserTest.kt`.
- Add coverage that `parseProviderSettingsProviders()` preserves `metadata.noteKey`, `metadata.note`, and `metadata.icon`.
- Add coverage that unknown fields remain tolerated.

JetBrains frontend:

- Update `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUiTest.kt`.
- Assert metadata notes render in the provider list.
- Assert generic fallback remains for providers without metadata.
- Assert provider icons are visible using metadata-driven icon selection.

### 12. Changeset

Add a patch changeset because this is user-facing UI behavior:

- Mention JetBrains provider settings now use shared provider descriptions and provider icons.
- Keep the text user-facing, not implementation-specific.

## Verification

Run the smallest relevant checks after implementation:

```bash
bun run script/check-opencode-annotations.ts
./script/generate.ts
```

From `packages/opencode/`:

```bash
bun run typecheck
bun test ./test/kilocode/<new-provider-metadata-test>.test.ts
```

From `packages/kilo-vscode/`:

```bash
bun run typecheck
```

From `packages/kilo-jetbrains/`:

```bash
./gradlew :backend:test --tests ai.kilocode.backend.cli.KiloCliDataParserTest
./gradlew :frontend:test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest
./gradlew typecheck
```

If JetBrains icon resource generation is implemented in Gradle, also verify `processResources` includes generated provider icons in the frontend resource output.

## Risks And Constraints

- Shared upstream-owned CLI files must have narrow `kilocode_change` markers. Keep Kilo-specific logic in `packages/opencode/src/kilocode/`.
- JetBrains cannot consume the Solid `ProviderIcon` component or the SVG sprite directly; it needs generated IntelliJ resources.
- Some provider SVGs use `currentColor`; generated JetBrains icon resources need literal color values and dark variants.
- If JetBrains localization generation is deferred, JetBrains descriptions will be shared but English-only. VS Code remains localized through `noteKey`.
- Keep compatibility fallbacks in VS Code and JetBrains so older CLI responses without metadata still render usable provider rows.
