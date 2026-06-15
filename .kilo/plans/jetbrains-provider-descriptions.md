# JetBrains Provider Descriptions

## Goal

Show CLI-provided provider descriptions in JetBrains provider settings, especially for popular catalog rows like OpenAI, without duplicating provider-description data in Kotlin.

## Findings

- JetBrains already has most of the infrastructure:
  - `ProviderSettingsProviderDto.description` and `metadata` in `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/ProviderSettingsDto.kt`.
  - `/provider` parsing in `KiloCliDataParser.parseProviderSettingsProviders()` preserves `description`, `metadata.noteKey`, `metadata.note`, and `metadata.icon`.
  - `ProviderCatalog.providerDescription()` already prefers `description`, then `metadata.noteKey`, then `metadata.note`.
  - `ProviderListRenderer` already has a secondary `desc` label and tests for metadata-driven notes.
  - `KiloBundle.properties` already contains note strings such as `settings.providers.note.openai`.
- The CLI already owns popular-provider display metadata in `packages/opencode/src/kilocode/provider/metadata.ts`.
- The `/provider` handler attaches that metadata in `packages/opencode/src/server/routes/instance/httpapi/handlers/provider.ts` with `metadata: providerMetadata(item.id)`.
- VS Code still has a client-side fallback mapping in `provider-catalog.ts`, but duplicating that in Kotlin should not be the primary fix because it creates another provider-description source of truth.
- If JetBrains is not showing descriptions, the likely issue is one of:
  - the JetBrains-launched CLI binary is stale and does not include provider metadata,
  - the `/provider` response reaching JetBrains does not include `description` or `metadata`,
  - the JetBrains parser is not seeing the actual field shape returned by the endpoint,
  - the renderer/layout hides the existing description label.

## Plan

1. Verify the data path before changing UI behavior.
   - Use existing JetBrains logs from `KiloBackendProviderSettingsManager.state()` to check, for a provider such as `openai`, whether these flags are true:
     - `description=...`
     - `note=...`
     - `noteKey=settings.providers.note.openai`
   - If logs are inconclusive, add a temporary local inspection while implementing, or use the backend HTTP endpoint directly in the sandbox, to confirm the raw `GET /provider?directory=...` response contains `metadata`.
   - Confirm the JetBrains run is using a freshly built CLI binary that includes `packages/opencode/src/kilocode/provider/metadata.ts` and the `/provider` handler metadata attachment.

2. Fix the source if the response is missing metadata.
   - Do not add Kotlin fallback descriptions first.
   - If the packaged/dev JetBrains CLI binary is stale, fix the dev/run/build flow so the JetBrains plugin launches the current CLI artifact.
   - Regenerate server/API artifacts if the provider schema or endpoint metadata contract changed:
     - Run `./script/generate.ts` from the repo root after any `/provider` schema or endpoint changes.
     - Confirm generated SDK/OpenAPI outputs include the optional provider `metadata` field if they are affected.
   - Rebuild the CLI binary used by JetBrains so the running plugin includes `packages/opencode/src/kilocode/provider/metadata.ts` and the `/provider` handler metadata attachment.
     - From `packages/kilo-jetbrains/`, use the existing build flow that prepares CLI binaries for the plugin, such as `bun run build --prepare-cli` when only refreshing generated CLI binaries is needed.
     - For a full plugin build, use `bun run build` from `packages/kilo-jetbrains/`.
     - If using a sandbox run configuration, verify it points at the rebuilt/generated CLI artifact rather than an older installed `kilo`.
   - If the `/provider` handler is not the endpoint being hit, update the JetBrains backend to call the metadata-bearing endpoint.
   - If the handler returns `metadata` but the schema strips it, fix the CLI schema/serialization path in `packages/opencode`, with narrow `kilocode_change` markers as already done for the existing metadata fields.

3. Fix the parser only if the raw payload includes metadata but the DTO does not.
   - `KiloCliDataParser.parseProviderSettingsProviders()` already parses `metadata.noteKey`, `metadata.note`, and `metadata.icon`; keep this path as-is unless the actual wire field names differ.
   - Add/adjust parser tests only for the observed wire shape.

4. Fix the renderer only if the DTO has a description but the UI hides it.
   - `ProviderListRenderer` already sets `desc.text = providerDescription(value.provider)` and hides it only when empty.
   - Inspect whether row height/layout/action overlay causes the label to be clipped in the actual settings list.
   - Keep the existing description priority and only adjust Swing layout if the value is present but not visible.

5. Keep the existing data priority intact.
   - `provider.description` from the CLI should still win over note metadata.
   - A localized bundle value from `noteKey` should win over the English `metadata.note` fallback.
   - The English `metadata.note` should still work if a bundle key is missing.

6. Add a Kotlin fallback mapping only as an explicit compatibility decision.
   - This fallback would mirror VS Code’s older client-side note-key mapping.
   - Only add it if product wants JetBrains to show notes when connected to an older/stale CLI that cannot provide metadata.
   - If added, keep it small, document it as compatibility-only, and continue to prefer CLI `description` and `metadata`.

7. Align row rendering with the visible VS Code behavior.
   - Show notes for catalog rows such as `Popular providers` and `All providers`.
   - Consider hiding generic note text for `Connected providers` if strict VS Code parity is desired, because VS Code connected rows show connection/source controls rather than provider marketing notes.
   - Keep Kilo Gateway special behavior unchanged: connected Kilo has no actions.

8. Update tests based on the actual fix.
   - If the fix is source/data-path related, keep or add backend/parser tests proving provider metadata reaches `ProviderSettingsProviderDto`.
   - If the fix is renderer/layout related, add a frontend test proving a provider with CLI metadata renders the expected text.
   - Only if a compatibility Kotlin fallback is added, add a renderer/helper test proving `OpenAI` with no metadata still renders `GPT and Codex models with API key or ChatGPT login`.
   - Keep the existing test that unknown providers do not receive invented descriptions.
   - Keep the existing test that explicit `provider.description` overrides metadata/fallback notes.
   - If connected-row notes are intentionally hidden, add a small test for connected rows.

9. Run focused verification from `packages/kilo-jetbrains/`.
   - Refresh the CLI binary first when testing runtime behavior:
     - `bun run build --prepare-cli`
   - `./gradlew :frontend:test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`
   - `./gradlew typecheck`
   - If a CLI/server source fix is needed, also run from repo root:
     - `bun run script/check-opencode-annotations.ts`
     - `./script/generate.ts`
   - If a new opencode test is added, run the targeted `bun test` from `packages/opencode/`.

## Expected Outcome

Popular provider rows in JetBrains settings show the same descriptive text users see in VS Code by consuming the CLI-owned provider metadata. Kotlin remains a renderer/client of provider descriptions, not a duplicated source of provider-description truth.

## Risks

- The screenshot may be from a stale dev CLI/backend binary. Fixing the run/build path is better than duplicating metadata in Kotlin.
- Adding fallback descriptions for connected rows could make JetBrains diverge from the VS Code connected-provider section. Prefer catalog-row descriptions only if exact visual parity is the goal.
- A Kotlin fallback mapping is acceptable only as compatibility with older CLI responses, not as the primary architecture.
