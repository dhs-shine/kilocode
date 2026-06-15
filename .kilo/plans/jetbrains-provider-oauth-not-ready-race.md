# JetBrains provider settings: "Kilo backend is not ready" after OAuth/connect

## Symptom

User clicks OAuth (e.g. OpenAI), authorizes in the browser, switches back, and the
settings UI shows an error with no connected provider. The OAuth itself actually
succeeded on the CLI (auth was saved), but the action RPC fails with:

```
RpcException: Remote call KiloProviderRpcApi#callback has failed:
IllegalStateException: Kilo backend is not ready
  at KiloBackendAppService.requireReady(KiloBackendAppService.kt:213)
  at KiloBackendProviderSettingsManager.state(KiloBackendProviderSettingsManager.kt:47)
  at KiloBackendProviderSettingsManager.callback(KiloBackendProviderSettingsManager.kt:102)
```

The user is left with providers=0 and an error overlay even though the connection worked.

## Root cause — a self-inflicted race between `dispose()` and `state()`

Every mutating provider action funnels through the same tail in
`KiloBackendProviderSettingsManager` (`packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/provider/KiloBackendProviderSettingsManager.kt`):

- `connect()` (84–89), `callback()` (98–103), `disconnect()` (105–134),
  `enable()` (136–141), `saveCustom()` (143–155) all end with:
  **`<mutate>` → `dispose()` (`POST /global/dispose`) → `state(directory)`**.
- `state()` begins with `app.requireReady()` (line 47), which throws immediately
  when `appState` is not `Ready`.

`dispose()` exists to force the CLI to drop its cached global App so the next reads
reflect the change just made. But the CLI also emits a `global.disposed` /
`server.instance.disposed` SSE event in response. The backend watches those events in
`KiloBackendAppService.startWatchingGlobalSseEvents()` (675–703) and, when the current
state is `Ready`, calls `load()` (689–698).

`load()` (293–426) immediately flips `_appState` to `KiloAppState.Loading` (line 301)
while it re-fetches config/profile/notifications, then returns to `Ready` ~0.5s later.

So the ordering that breaks:

1. Action mutates auth/config, then `dispose()` returns `200`.
2. The CLI's `global.disposed` SSE event is processed by the event watcher →
   `load()` → `_appState = Loading`.
3. The action's trailing `state(directory)` runs → `app.requireReady()` sees `Loading`
   → throws `Kilo backend is not ready`.
4. The whole action RPC fails. Frontend shows an error and no connected provider,
   even though the auth write succeeded.

The frontend logs confirm the backend recovers to `Ready` (`WorkspaceReady`) ~0.5s
after the failure — it was a transient `Loading` window, not a real outage.

This is racy for every action, but surfaces most reliably for OAuth because the
callback round-trip is slow (~8.8s observed), which widens the window for the
SSE-triggered `load()` to land exactly before the trailing `state()`.

### Why the earlier fixes in this session don't cover it

- The renderer `ClassCastException` fix and the frontend `action()` try/catch
  (returning a `ProviderActionResultDto(state(dir), error=…)`) are still correct and
  should stay. But the frontend fallback re-calls `state(dir)`, which **also** races
  with the same `Loading` window and fails again (the logs show the fallback `state`
  RPC failing too). The fix must live on the backend.

## Primary fix — await readiness through the transient `Loading` window

Add a bounded `awaitReady()` to `KiloBackendAppService` and use it in the provider
manager's `state()` instead of the immediate `requireReady()`. Because every action
ends by calling `state()`, fixing the single chokepoint covers connect / callback /
disconnect / enable / saveCustom **and** the direct UI load path.

### 1. `KiloBackendAppService.awaitReady()` (new)

In `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/app/KiloBackendAppService.kt`,
next to `requireReady()` (209–215). Mirrors the existing `awaitLoadResult()` pattern
(620–624) and the workspace RPC's "wait for Ready" approach
(`KiloWorkspaceRpcApiImpl.state`, 94–102).

```kotlin
suspend fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS) {
    when (_appState.value) {
        is KiloAppState.Ready -> return
        is KiloAppState.MigrationRequired -> throw IllegalStateException("Migration required")
        // Transient post-dispose / startup states — wait for them to settle.
        is KiloAppState.Loading, KiloAppState.Connecting -> {
            val settled = withTimeoutOrNull(timeoutMs) {
                appState.first { it !is KiloAppState.Loading && it !is KiloAppState.Connecting }
            }
            when (settled) {
                is KiloAppState.Ready -> return
                is KiloAppState.MigrationRequired -> throw IllegalStateException("Migration required")
                else -> throw IllegalStateException("Kilo backend is not ready")
            }
        }
        // Genuinely down — fail fast, same as requireReady().
        else -> throw IllegalStateException("Kilo backend is not ready")
    }
}
```

- Add `private const val READY_TIMEOUT_MS = 5_000L` to the companion (91–103).
- Add `import kotlinx.coroutines.withTimeoutOrNull` (`withTimeout`/`first` already imported).
- Rationale for 5s: observed recovery is ~0.5s, giving ~10× margin, and it stays well
  under the frontend RPC budget (`KiloProviderService.RPC_TIMEOUT_MS = 20_000`) even
  after adding the OAuth round-trip and the post-ready provider fetches. Only the
  transient `Loading`/`Connecting` states wait; `Disconnected`/`Error` still fail fast
  so a genuinely-down backend doesn't hang the UI.

### 2. Use it in the provider manager

`KiloBackendProviderSettingsManager.state()` (line 47):

```kotlin
- app.requireReady()
+ app.awaitReady()
```

No other manager methods change — they reach readiness through `state()`.

### Why this is correct and minimal

- The only transient state that `dispose()` induces is `Loading` (the SSE handler only
  calls `load()` when currently `Ready`; the HTTP server and SSE stay connected, so the
  connection state does not drop). Waiting `Loading → Ready` is exactly the gap.
- If `state()` happens to run *before* the SSE event is processed, `appState` is still
  `Ready` → `awaitReady()` returns instantly and proceeds; any per-resource fetch that
  races the dispose is already caught by `state()`'s `load(resource, errors){}` wrapper
  (178–190) and returned as a soft `errors` entry, not a hard RPC failure.
- If the reload fails (`Loading → Error`), `awaitReady()` returns from `first{…}` on the
  `Error` state and throws promptly — no full-timeout stall.

### Scope decision

Keep the change limited to the provider manager's `state()`. The session/workspace RPCs
also use `requireReady()`, but they are out of scope for this bug and have their own
readiness handling (workspace emits `PENDING`). Broadening `requireReady → awaitReady`
everywhere is a larger behavior change and not needed here.

## Secondary fix (included) — OAuth authorize/callback HTTP timeout

The first OAuth attempt failed earlier with `Read timed out` after 15s; the retry
succeeded at 8.8s. The manager's `request()` helper hardcodes a 15s call/read timeout
(`CALL_TIMEOUT_SECONDS = 15`, used in `request()` 207–220). OAuth `authorize`/`callback`
do a provider-side code exchange that can exceed 15s under load.

The frontend bounds the whole action at `RPC_TIMEOUT_MS = 20_000`
(`KiloProviderService`), so the backend timeout and the frontend RPC budget must be
raised **together** — a longer backend timeout alone would just trip the frontend RPC
timeout first.

### Backend — per-call timeout for the OAuth paths

In `KiloBackendProviderSettingsManager.kt`:

- Companion (33–42): add `private const val OAUTH_CALL_TIMEOUT_SECONDS = 60L` next to
  `CALL_TIMEOUT_SECONDS`.
- `request()` (207): add `timeoutSeconds: Long = CALL_TIMEOUT_SECONDS` and use it for
  both `callTimeout(...)` and `readTimeout(...)` (211–212).
- `post()` (193): add `timeoutSeconds: Long = CALL_TIMEOUT_SECONDS`, forwarded to
  `request(...)`. `get`/`put`/`patch`/`deleteAuth`/`dispose` keep the default.
- `authorize()` (91–96): `post(".../oauth/authorize…", body, OAUTH_CALL_TIMEOUT_SECONDS)`.
- `callback()` (98–103): `post(".../oauth/callback…", body, OAUTH_CALL_TIMEOUT_SECONDS)`.
  The trailing `dispose()`/`state()` keep their defaults.

### Frontend — matching RPC budget for the OAuth paths

In `KiloProviderService.kt`:

- Companion (32–35): add `private const val OAUTH_RPC_TIMEOUT_MS = 90_000L` next to
  `RPC_TIMEOUT_MS`.
- `call()` (37): add `timeoutMs: Long = RPC_TIMEOUT_MS`, used in `withTimeout(timeoutMs)`.
- `action()` (68): add `timeoutMs: Long = RPC_TIMEOUT_MS`, forwarded to `call(...)`.
- `authorize(...)` (61): `call("authorize…", OAUTH_RPC_TIMEOUT_MS) { authorize(input) }`.
- `callback(...)` (62): `action(input.directory, OAUTH_RPC_TIMEOUT_MS) { callback(input) }`.

Budget check: backend `callback` worst case ≈ `OAUTH_CALL_TIMEOUT_SECONDS` (60s) +
`awaitReady` (≤5s) + provider fetches (~1s) ≈ ~66s, so the frontend OAuth budget of 90s
leaves margin. All four values are constants and easy to tune later.

### Optional refinement — surface timeouts instead of swallowing them

`withTimeout` throws `TimeoutCancellationException`, a `CancellationException` subclass.
`ProvidersSettingsUi.launch()` (170–192) currently treats all `CancellationException` as
silent (logs "cancelled", shows nothing) — correct for stale/disposed cancellation, but
it means a true RPC timeout shows no message. Optionally add a `catch
(e: TimeoutCancellationException)` **before** the `CancellationException` catch that calls
`showError(...)` (guarded by `active(id)` on the EDT) and does not rethrow. Keeps genuine
cancellation silent while making a real timeout visible. Low risk; include if desired.

## Tests

Backend, real `KiloBackendAppService` + `MockCliServer` + `FakeCliServer`
(`packages/kilo-jetbrains/backend/src/test/kotlin/ai/kilocode/backend/provider/KiloBackendProviderSettingsManagerTest.kt`
and/or `app/KiloBackendAppServiceTest.kt`). No mocking of threading — drive the real
SSE event + REST gate, matching existing tests that already use
`MockCliServer.pushEvent(...)` and `responseGate`.

1. **`state()` waits through a dispose-triggered reload instead of failing** (the
   regression). Build app, connect, await `Ready`. Install `mock.responseGate =
   CountDownLatch(1)` so REST blocks; `mock.pushEvent("global.disposed", "{}")` to drive
   `load()` → `Loading`. Launch `manager.state("/test")` in `async`; assert it is **not**
   completed after a short settle (still awaiting). Release the gate; await the result
   and assert it returns a valid `ProviderSettingsDto` (no throw) with the mock's
   providers. Fails today (throws `Kilo backend is not ready`), passes after the fix.

2. **`awaitReady()` returns immediately when already Ready** — sanity/fast path.

3. **`awaitReady()` fails fast when Disconnected/Error** — assert it throws promptly
   (well under the timeout) so a genuinely-down backend doesn't hang.

4. (Optional) **End-to-end action**: with the app forced into `Loading` via gated reload,
   a `connect()`/`callback()` returns a populated result rather than throwing — confirms
   all actions inherit the fix via `state()`.

## Validation

From `packages/kilo-jetbrains/`:

- `./gradlew :backend:test --tests ai.kilocode.backend.provider.KiloBackendProviderSettingsManagerTest`
- `./gradlew :backend:test --tests ai.kilocode.backend.app.KiloBackendAppServiceTest` (if a test is added there)
- `./gradlew typecheck`

## Files touched

- `backend/.../app/KiloBackendAppService.kt` — add `awaitReady()` + `READY_TIMEOUT_MS` + `withTimeoutOrNull` import.
- `backend/.../provider/KiloBackendProviderSettingsManager.kt` — `requireReady()` → `awaitReady()` in `state()`; add `OAUTH_CALL_TIMEOUT_SECONDS`, per-call timeout on `request()`/`post()`, and apply it in `authorize()`/`callback()`.
- `frontend/.../app/KiloProviderService.kt` — add `OAUTH_RPC_TIMEOUT_MS`, `timeoutMs` params on `call()`/`action()`, apply to `authorize()`/`callback()`.
- (Optional) `frontend/.../settings/providers/ProvidersSettingsUi.kt` — surface `TimeoutCancellationException` in `launch()`.
- `backend/src/test/.../provider/KiloBackendProviderSettingsManagerTest.kt` (and/or `app/KiloBackendAppServiceTest.kt`) — new tests.
- Reuse the existing changeset `.changeset/fix-jetbrains-provider-settings.md` (extend its text to mention the connect/OAuth not-ready fix and the OAuth timeout bump).

## Risks

- A real reload that never reaches `Ready` within 5s makes `state()` throw after the
  timeout (same user-visible "not ready" error as today, just delayed up to 5s) — only
  in a genuinely-broken backend, which already errors.
- `state()` is now `suspend`-blocking up to 5s in the worst transient case; bounded and
  well under the (non-OAuth) 20s RPC budget.
- The OAuth budget bump lets the UI wait up to ~90s for a genuinely stuck OAuth exchange.
  Mitigated by the loading overlay and the existing job-cancel on dispose/new action; the
  optional timeout-surfacing refinement makes a true timeout visible.

## Decisions

- Include the OAuth authorize/callback timeout bump together with the not-ready race fix
  (per user), coupling the backend per-call timeout (60s) with the frontend OAuth RPC
  budget (90s) so they cannot trip each other.
