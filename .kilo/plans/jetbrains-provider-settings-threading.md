# Fix JetBrains Provider Settings Threading

## Goal
Fix the JetBrains Provider Settings configurable so it stops showing `Loading providers` once provider state has loaded, does not repaint or mutate Swing from the wrong thread, and ignores stale async work after reloads, actions, or configurable disposal.

## Findings
- `ProvidersSettingsUi` uses `Dispatchers.Main` for Swing work, while JetBrains package guidance and existing settings code use `Dispatchers.EDT + ModalityState.any().asContextElement()`.
- `ProvidersSettingsUi.connect()` and `custom()` read dialog Swing fields from a background coroutine after `showAndGet()` returns.
- `ProvidersSettingsUi.launch()` catches all `Exception`, including `CancellationException`, and has no current-request or disposed guard before applying provider state.
- `ProvidersConfigurable.disposeUIResources()` delays cancellation when called off EDT because scope cancellation happens inside `invokeLater`, leaving a window for stale provider coroutines to repaint or keep the loading overlay visible.
- Provider settings tests currently exercise Swing components directly instead of consistently using the real EDT, so they do not protect the threading contract described in `packages/kilo-jetbrains/AGENTS.md`.

## Implementation Plan
1. Keep provider RPC off the EDT.
   - Continue launching provider state/action work from the configurable-owned `Dispatchers.Default` scope.
   - Do not call `KiloProviderService` from EDT.

2. Move all provider Swing work to the IntelliJ EDT dispatcher.
   - In `ProvidersSettingsUi.kt`, add an `edt` coroutine context using `Dispatchers.EDT + ModalityState.any().asContextElement()`.
   - Replace every `withContext(Dispatchers.Main)` with `withContext(edt)`.
   - Add `@RequiresEdt` plus a small runtime EDT check to provider UI methods that create, mutate, or read Swing state, especially `reload`, `syncLoading`, `apply`, error display, `ProvidersContent.update`, and list/search selection helpers touched by the fix.

3. Snapshot dialog input before background work starts.
   - In `connect()`, read `dialog.key()` and `dialog.metadata()` immediately after `showAndGet()` on EDT, then pass plain values into the coroutine.
   - In `custom()`, build `CustomProviderSaveDto` on EDT before launching `saveCustom`.
   - Keep OAuth browser and code prompt inside `withContext(edt)`.

4. Add stale request and disposal protection.
   - Track a monotonically increasing request token in `ProvidersSettingsUi`.
   - Increment the token when a reload or provider action starts.
   - Cancel the previous provider job when a new reload/action starts.
   - In `apply` and error handling, update the UI only when the token is still current and the UI has not been disposed.
   - Catch `CancellationException` before broad `Exception` and rethrow or return without showing an error overlay.

5. Fix configurable disposal ordering.
   - In `ProvidersConfigurable.disposeUIResources()`, cancel the coroutine scope immediately when disposal starts.
   - Dispose the Swing panel on EDT, setting the provider UI disposed flag and cancelling its current job.
   - Preserve the existing rule that Swing disposal itself runs on EDT.

6. Optionally annotate shared overlay helpers if required by the new checks.
   - Add `@RequiresEdt` to `SettingsOverlayPanel.showProgress`, `showError`, `clearProgress`, and `syncOverlay` if provider checks expose unannotated UI mutation paths.
   - Keep this minimal and avoid behavior changes outside threading guarantees.

7. Add focused tests.
   - Add `FakeProviderRpcApi` under `frontend/src/test/kotlin/ai/kilocode/client/testing/`, using `assertNotEdt` for every RPC method and `CompletableDeferred` gates for delayed provider state responses.
   - Update `ProvidersSettingsUiTest` so Swing creation, mutation, and inspection happen via `ApplicationManager.getApplication().invokeAndWait` plus EDT event draining, matching existing models/settings tests.
   - Add a provider UI lifecycle test that completes a provider state load and asserts the loading overlay clears and provider rows appear.
   - Add a stale response test where an older gated reload completes after a newer reload and is ignored.
   - Add a dispose test where an in-flight provider reload is cancelled or ignored after `dispose()`, with no error overlay or late UI mutation.

8. Add release note coverage.
   - Add a patch changeset for `@kilocode/kilo-jetbrains` unless an existing JetBrains provider settings changeset on this branch should be extended instead.
   - Suggested wording: `Fix JetBrains provider settings loading and navigation stability.`

## Verification
- Run the targeted provider settings tests from `packages/kilo-jetbrains/`, for example `./gradlew test --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest`.
- Run `./gradlew typecheck` from `packages/kilo-jetbrains/`.
- If the targeted changes touch shared settings base classes, run the affected settings tests as well, such as models/settings UI tests.

## Expected Files
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersConfigurable.kt`
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUiTest.kt`
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/testing/FakeProviderRpcApi.kt`
- Optional: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/base/SettingsOverlayPanel.kt`
- Optional: `.changeset/<slug>.md`
