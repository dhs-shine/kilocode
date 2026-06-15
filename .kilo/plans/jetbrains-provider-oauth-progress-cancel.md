# JetBrains Provider OAuth Progress And Locking Plan

## Goal

Replace the generic provider OAuth `Loading providers...` overlay with a shared settings progress UI that can show an updating message, countdown, and cancel button. While any provider operation is in flight, prevent starting another provider operation and render provider rows disabled with no action buttons. If the user cancels OAuth, do not refresh provider state; just close the progress UI and restore the existing provider list.

## Current State

- `ProvidersSettingsUi` extends `SettingsPanel`, which extends `SettingsOverlayPanel`.
- `SettingsOverlayPanel` owns `SettingsProgressOverlay`, currently text-only with `showProgress(text)`, `showError(text)`, and `clearProgress()`.
- Provider `reload`, API-key connect, OAuth, disconnect, enable, and custom save all use one `job`/`request` pipeline in `ProvidersSettingsUi.launch`.
- `launch` currently cancels the existing job when a new provider action starts, allowing action replacement rather than blocking concurrent actions.
- Provider OAuth currently calls `syncLoading()` before authorize/callback, so the overlay says `Loading providers...` during browser authorization.
- `LoggedOutProfileUi` already has the desired user-facing countdown copy pattern: `profile.login.waitingTimed=Waiting for authorization... ({0})` with a cancel button.
- Provider rows expose actions through `ProviderListRow.actions`; rendering/hit-testing already uses visible action lists and `row.enabled(action)`.

## Design

### Shared Settings Progress UI

Update the shared settings superclass stack, not just provider UI:

- Extend `SettingsProgressOverlay` to retain:
  - one message `JBLabel`
  - an optional cancel `JButton`
  - the existing `INFO`/`ERROR` kind
- Preserve current APIs:
  - `showProgress(text: String)` remains text-only and non-cancellable.
  - `showError(text: String)` remains text-only and non-cancellable.
  - `clearProgress()` hides overlay and removes/clears cancel action.
- Add a cancellable progress API on `SettingsOverlayPanel`, for example:
  - `showProgress(text: String, cancelText: String, cancel: () -> Unit)`
  - or `showProgress(SettingsProgress(text, cancelText, cancel))` if a tiny data class reads cleaner.
- Add `updateProgress(text: String)` on `SettingsOverlayPanel`/`SettingsProgressOverlay` so countdown ticks can update only the message without resetting the cancel action or rebuilding components.
- Keep overlay placement in `SettingsOverlayPanel` unchanged.
- Use existing style tokens (`UiStyle.Gap`, overlay colors) and IntelliJ components (`JBLabel`, `JButton`).

### Provider Operation State

In `ProvidersSettingsUi`:

- Replace the current “cancel previous job on every launch” behavior for user actions with single-flight guarding.
- Track whether an operation is active, e.g. `private var busy = false` plus the existing `job` and `request` generation.
- When `busy` is true:
  - `connect`, `oauth`, `disconnect`, `enable`, `custom`, and `reload` should return without starting another operation.
  - `ProviderToolbarAction.update` should disable add/refresh actions.
  - Provider content should render all rows disabled and with no action buttons.
- Keep disposal behavior: `dispose()` still invalidates the request and cancels the current job.

### Disabled Provider Rows With No Buttons

Update provider list state in the existing model path:

- Add a `busy` flag to `ProvidersContent`, default false.
- Add `setBusy(busy: Boolean)` or include it in `update(state, busy)`.
- When busy changes, call `sync()` so the existing rows are rebuilt.
- Prefer the smallest row-level change:
  - Add `disabled: Boolean = false` to `ProviderListRow`.
  - Return `false` from `ProviderListRow.enabled(action)` when disabled.
  - Make `ProviderListRenderer.visibleActions(row, selected)` return `emptyList()` when `row.disabled` is true.
  - In `providerListRows`, accept an optional `disabledRows: Boolean = false` and pass it into every row.
- This keeps all provider names/descriptions visible but removes connect/OAuth/disconnect/enable buttons and makes hit-testing/keyboard primary action no-op.
- Also disable the search field and list selection/interaction while busy if practical, but the key requirement is no action buttons and no action execution.

### OAuth Countdown And Cancel

In `ProvidersSettingsUi.oauth(provider)`:

- Do not call `syncLoading()` for OAuth.
- Start the operation with a provider-specific message, for example:
  - Initial authorize request: `Starting OAuth for {0}...`
  - Callback wait: reuse the profile wording style: `Waiting for authorization... ({0})`
- Add bundle strings:
  - `settings.providers.oauth.starting=Starting OAuth for {0}...`
  - `settings.providers.oauth.waitingTimed=Waiting for authorization... ({0})`
  - `settings.providers.oauth.cancel=Cancel`
  - optionally `settings.providers.oauth.failed=OAuth failed` if needed for user-facing errors.
- Start a Swing `Timer(1000)` only while waiting for callback.
- Countdown source:
  - Use the provider service OAuth timeout as the expiry target if exposed as an internal constant, or duplicate no magic by defining a local provider UI constant matching the actual 90-second frontend timeout.
  - Display `m:ss`, matching `LoggedOutProfileUi.syncTime()`.
- Flow:
  - Set busy before starting authorize.
  - Show cancellable progress with `Starting OAuth for provider.name...` and a cancel callback.
  - Run `authorize` in the coroutine.
  - On EDT after authorize returns, check request is still active; open browser if `ready.url` is present; for `method == "code"`, show the code input dialog.
  - If the code dialog is cancelled or returns blank when code is required, treat it as user cancellation: stop timer, clear progress, restore busy false, and do not call callback or reload.
  - Before `callback`, start/restart the timer and update progress to `Waiting for authorization... (1:30)` with the same cancel action.
  - On success, apply returned provider state as today.
  - On failure, show error and restore provider actions.
  - On cancel, cancel the job and clear progress without calling `apply`, `state`, workspace reload, or profile refresh.
- Make cancel idempotent:
  - Increment `request` or mark the current request cancelled.
  - Cancel `job`.
  - Stop the countdown timer.
  - Set `busy = false`.
  - Clear the progress overlay.
  - Refresh content from existing `state` only by re-rendering with `busy = false`, not by fetching fresh state.

### Non-OAuth Actions

For reload/API-key connect/disconnect/enable/custom save:

- Keep the generic progress text unless a better action-specific string already exists.
- Use the same `busy` guard so no action can start while another is running.
- On completion/failure, restore `busy = false` and re-render provider rows.
- For non-cancelled operation failures, preserve current behavior: error overlay remains visible and state is updated when an action result includes state.
- Do not add a cancel button for ordinary provider operations unless it falls out naturally from the shared API; the explicit cancel requirement is for OAuth waiting.

### Concurrency And Stale Results

- Keep request-generation checks (`active(id)`) to ignore stale completions.
- Do not cancel an existing user action when a new click occurs; ignore new clicks while busy instead.
- `reload()` from init should still run normally.
- If the user clicks refresh while OAuth is active, it should be disabled/no-op.
- If an operation is cancelled by the user, stale coroutine completions must be ignored and must not show an error overlay.
- `CancellationException` from user cancellation should not be logged as a failure and should not show an error.

## Files To Change

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/base/SettingsProgressOverlay.kt`
  - Add cancel button support and message update support.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/base/SettingsOverlayPanel.kt`
  - Expose cancellable progress and message update methods.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUi.kt`
  - Add busy/single-flight state.
  - Add OAuth-specific progress/cancel/countdown flow.
  - Disable toolbar actions while busy.
  - Restore existing state without refresh on cancel.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRows.kt`
  - Add row disabled support and optional disabled-row generation.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/providers/ProviderListRenderer.kt`
  - Hide all action labels while a row is disabled.
- `packages/kilo-jetbrains/frontend/src/main/resources/messages/KiloBundle.properties`
  - Add OAuth progress/cancel strings.
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/base/SettingsRowsTest.kt`
  - Add shared overlay tests for cancel button retention, message update, and clear behavior.
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/settings/providers/ProvidersSettingsUiTest.kt`
  - Add provider-specific tests for OAuth progress, cancel/no-refresh, and disabled actions.
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/testing/FakeProviderRpcApi.kt`
  - Add OAuth authorize/callback call tracking and optional deferred gates for deterministic tests.

## Tests To Add

### Shared Settings Overlay

- `showProgress(text, cancelText, cancel)` renders one message label and one cancel button.
- `updateProgress(text)` updates the retained label and keeps the same cancel button/listener.
- Clicking cancel invokes the supplied callback.
- `showProgress(text)` after cancellable progress removes/hides the cancel button.
- `showError(text)` removes/hides the cancel button and uses error colors.
- `clearProgress()` hides overlay and removes/hides cancel button.

### Provider UI

- OAuth start shows provider OAuth-specific progress text, not `Loading providers...`.
- OAuth callback waiting updates to `Waiting for authorization... (m:ss)` and shows `Cancel`.
- While OAuth is active:
  - toolbar add/refresh actions are disabled or no-op.
  - provider rows remain visible.
  - renderer action labels are empty for selected/unselected rows.
  - mouse/keyboard activation does not invoke another provider action.
- Cancel during OAuth:
  - cancels the current job.
  - clears progress.
  - restores provider row actions.
  - does not call `callback` if cancelled before callback starts.
  - does not call `state` again beyond the initial load.
  - does not apply stale completion if the fake deferred completes later.
- Operation single-flight:
  - triggering `reload()` while an OAuth/action is busy does not increment `stateCalls`.
  - triggering another action while busy does not add to fake RPC action call lists.
- Existing stale reload and dispose tests still pass.

## Verification

Run the smallest relevant checks from `packages/kilo-jetbrains/`:

```bash
./gradlew :frontend:test --tests ai.kilocode.client.settings.base.SettingsRowsTest --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest
./gradlew typecheck
```

## Notes

- Keep all Swing mutations on EDT and annotate new UI methods with `@RequiresEdt`.
- Use `javax.swing.Timer` for countdown so ticks run on EDT.
- Avoid adding backward-compatible complexity beyond preserving the current public `showProgress(text)`, `showError(text)`, and `clearProgress()` call sites.
- Do not refresh provider state on user cancellation; just restore the current in-memory `state` with `busy = false`.
