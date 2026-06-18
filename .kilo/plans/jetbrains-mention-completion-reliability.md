# Make JetBrains `@`/`/` prompt completion as reliable as editor completion

Scope: Kilo-owned frontend code under
`packages/kilo-jetbrains/frontend/.../session/ui/prompt/`. No shared/upstream files, so
no `kilocode_change` markers needed.

This is a follow-up to `jetbrains-empty-mention-completion.md` (prewarm +
`ALWAYS_AUTO_POPUP` removal, already implemented). It fixes the remaining instability.

## Symptoms (from user)

- Open `@`, then **type fast** (e.g. `@backend`) → the lookup **disappears**.
- **Type slowly** → it stays and narrows fine.
- The popup also **flickers** and **shows below the caret for a moment** before settling
  above.

## Root cause (verified against `$INTELLIJ_REPO`)

The provider fetches results **asynchronously on its own coroutine scope** and then forces
the open lookup to repaint via a **manual restart**:

- `KiloPromptCompletionProvider.search()` (`KiloPromptCompletionProvider.kt:198-207`)
  returns an **empty placeholder** immediately for a cold prefix and launches a debounced
  `refresh()` (`:215-228`).
- When `refresh()` finishes it stores `cache[prefix]` and posts `onRefresh()`
  (`:224-225`) → `PromptPanel.restartCompletion()` (`PromptPanel.kt:561-569`) →
  `CompletionProgressIndicator.scheduleRestart()`.

`scheduleRestart()` does not repaint in place. It `cancel()`s the current indicator and
schedules a brand-new completion through the `CommittingDocuments` phase
(`CompletionProgressIndicator.java:913-941` → `CompletionPhase.kt:234-262`). That async
path commits documents in a non-blocking read action and then, on the EDT, runs
`startAsyncCompletionIfNotExpired` (`CompletionPhase.kt:294-340`). It contains:

```kotlin
if (phase.myTracker.hasAnythingHappened() && (phase.indicator == null || !phase.indicator.lookup.isShown)) {
  phase.cancelPhase()
  CompletionServiceImpl.setCompletionPhase(NoCompletion)   // <- popup disappears
  return
}
```

So when our async `onRefresh → scheduleRestart` lands **while the user is still typing**
(`ActionTracker.hasAnythingHappened()` is true) and the lookup is in a transient
not-`isShown` window (it is, because our cold-prefix path produced only the transient
"Searching files…" element), the phase is dropped to `NoCompletion` and the lookup is
gone. Slow typing avoids the overlap, which is why it only reproduces under fast typing.

Why the editor's own completion does **not** disappear under fast typing: every keystroke
restart goes through the same code, but the editor's contributors return their items
**synchronously inside the background completion calculation**, so the lookup is already
`isShown` with real items and the `hasAnythingHappened()` branch is skipped (the comment
on `CompletionPhase.kt:327-328` says a shown lookup "is going to handle close by itself").
Our extra, arbitrarily-timed manual restart is the thing that races.

The "shows below then jumps above" flicker is a related but separate timing bug:
`showCompletion` sets `LookupPositionStrategy.ONLY_ABOVE` **after** `invokeCompletion`
returns (`PromptPanel.kt:555-558`), i.e. after the lookup has already been created and
positioned at the default `PREFER_BELOW` (`LookupPresentation.kt:24`). The disappear →
recreate cycle makes this visible repeatedly.

### Key platform facts that make the fix safe

1. **Completion contributors run off the EDT, on a background thread, inside a cancellable
   read action.** `CodeCompletionHandlerBase.startContributorThread`
   (`CodeCompletionHandlerBase.java:414-438`) runs `indicator.runContributors` via
   `AsyncCompletion.startThread` (`CompletionThreading.kt:118-153`,
   `Dispatchers.IO.limitedParallelism(5)`). The indicator picks `AsyncCompletion` whenever
   completion is not invoked inside a write action
   (`CompletionProgressIndicator.java:200-202`). Therefore a **blocking** `searchFiles`
   call inside `fillCompletionVariants`, guarded by `runBlockingCancellable`, is the
   platform-intended pattern (same as index-backed file/class completion) — it does not
   touch the EDT, and `FakeWorkspaceRpcApi.searchFiles` already asserts not-EDT, which the
   existing unit-test path satisfies today via `fetch()`.
2. **The platform already drives "background updates" of an open lookup.** While the
   contributor runs, the lookup shows the native calculating state and the
   `CompletionConsumer` adds items to the live lookup as they are produced. This is the
   mechanism to "reuse" — we do not need our own debounce/refresh/scheduleRestart.
3. **Restart reuses the same lookup instance.** `obtainLookup`
   (`CodeCompletionHandlerBase.java:274-298`) returns the existing completion lookup via
   `markReused()`, so a `LookupPresentation` set once persists across prefix-change
   restarts.
4. **The lookup is activated before it is shown.** `ClientLookupManager.createLookup`
   fires `LookupManagerListener.activeLookupChanged(null, lookup)`
   (`ClientLookupManager.kt:91-93`) before `indicator.showLookup()`. Setting the position
   strategy in that listener positions the lookup correctly on first paint — no flash.
5. `restartCompletionOnAnyPrefixChange()` (`KiloPromptCompletionProvider.kt:160,174`) is
   what makes the platform re-run our contributor (and thus a fresh search) for each new
   prefix. Keep it; it is the supported per-keystroke restart trigger.

## Plan

### Part 1 — Compute results synchronously on the completion background thread

Make the production path do what the unit-test path already does: block on the backend
search (with cancellation) inside `fillCompletionVariants`, and let the platform manage
the lookup. Delete the async refresh + manual restart entirely.

In `KiloPromptCompletionProvider.kt`:

- **Replace `search()`** (`:198-207`) with a cache-or-fetch that always returns a settled
  result:

  ```kotlin
  private fun search(prefix: String): FileSearchResultDto = cache[prefix] ?: fetch(prefix)
  ```

  `fetch()` (`:209-213`) stays as-is: `runBlockingCancellable { service.searchFiles(...) }`
  + `cache[prefix] = result`. It now runs on the async completion thread in production too.
- **Delete** `refresh()` (`:215-228`), the `onRefresh` field (`:48-49`), the `job`/`want`
  `@Volatile` fields (`:42-46`), and `SEARCH_DELAY_MS` (`:51-53`).
- **Delete** the `Search` data class (`:322`) and the `pending` plumbing. Simplify
  `mention()` (`:173-196`): drop the `search.pending` branch and the
  `prompt.mention.searching` placeholder; keep the `indexing` branch and the `noMatches`
  fallback. The native lookup spinner now covers the "in flight" state.
- **`clearMentions()`** (`:68-76`): keep clearing `paths/exists/pending/cache`; drop
  `want`/`job` resets.
- Remove now-unused imports: `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`,
  `com.intellij.openapi.application.ApplicationManager` (was only used for the
  unit-test-mode branch and the `invokeLater` in `refresh`). Keep `launch`/`CoroutineScope`
  (used by `prewarm` and `validate`) and `runBlockingCancellable`.
- Keep `prewarm()` and `restartCompletionOnAnyPrefixChange()` unchanged. Prewarm keeps the
  first `@` instant (cache hit, no blocking); per-prefix restarts hit the cache when the
  prefix repeats and otherwise block briefly with native cancellation.

In `PromptPanel.kt`:

- **Delete** the `completion?.onRefresh = { ... restartCompletion() }` wiring in `init`
  (`:250-252`), the `completion?.onRefresh = null` line in `removeNotify` (`:451`), and the
  `restartCompletion()` method (`:561-569`).
- Remove the now-unused import
  `com.intellij.codeInsight.completion.impl.CompletionServiceImpl` (`:25`).

Remove the unused `prompt.mention.searching` key from
`frontend/src/main/resources/messages/KiloBundle.properties:167` (no locale file
references it). Keep `prompt.mention.indexing`.

### Part 2 — Position the lookup above the caret before first paint

Set `ONLY_ABOVE` as soon as the completion lookup for our editor is created, via a
project-level `LookupManagerListener`, instead of after `invokeCompletion`.

In `PromptPanel.kt`:

- Add a `private var lookupBus: MessageBusConnection? = null` and a `bindLookup()` that
  subscribes once to `LookupManagerListener.TOPIC` on `project.messageBus`:

  ```kotlin
  connection.subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, next ->
      val lookup = next as? LookupImpl ?: return@LookupManagerListener
      if (lookup.editor !== editor.getEditor(false)) return@LookupManagerListener
      lookup.presentation = LookupPresentation.Builder(lookup.presentation)
          .withPositionStrategy(LookupPositionStrategy.ONLY_ABOVE)
          .build()
  })
  ```

  Call `bindLookup()` from `addNotify()` (next to `bindRoot()`/`bindKeymap()`); disconnect
  in `removeNotify()` (`lookupBus?.disconnect(); lookupBus = null`).
- **Simplify `showCompletion()`** (`:551-559`) to just open completion
  (`invokeCompletion(project, ed, 1)`); drop the post-invoke presentation block — the
  listener now owns positioning for both the `@`/`/` document-triggered open and the
  completion-shortcut open, and it persists across restarts (reused lookup).
- Add import `com.intellij.codeInsight.lookup.LookupManagerListener`. Existing
  `LookupManager`, `LookupImpl`, `LookupPresentation`, `LookupPositionStrategy` imports stay
  (now used by the listener).

## Tests

`KiloPromptCompletionProviderTest` (provider built directly; `myFixture.completeBasic()`
runs the real off-EDT completion calculation):

- All existing tests stay green. They already exercise the blocking `fetch()` path (the old
  `isUnitTestMode` branch) and assert exact `rpc.searchQueries`; `completeBasic()` issues a
  single search per invocation, so query counts are unchanged. `test prewarm serves blank
  mention completion from cache` and `test mention completion reuses identical prefix
  result` still hold (cache hit ⇒ no extra query); `test clearing mentions resets cached
  prefix result` still holds (cache cleared ⇒ refetch).
- The `prompt.mention.searching` placeholder is no longer produced; confirm no test asserts
  it (none do — only `noMatches()` and `indexing` are asserted/relevant).

`PromptPanelTest` (real editor + lookup via `invokeCompletionAction` + `waitForLookupItems`):

- Existing `test prompt local completion shortcut opens mention lookup` / `... slash lookup`
  stay green and now cover the production blocking path end to end.
- **Add** `test prompt completion lookup is positioned above caret`: open the mention
  lookup as in the existing shortcut test, then assert
  `(LookupManager.getActiveLookup(editor) as LookupImpl).presentation.positionStrategy ==
  LookupPositionStrategy.ONLY_ABOVE`.

Note on the disappear/flicker race: it is an EDT/timing interaction that cannot be asserted
deterministically in a unit test. Coverage is the deterministic position test above plus
the existing real-lookup tests; the fast-typing regression is validated manually in
`./gradlew runIde`.

## Files to change

- `frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt` — blocking
  `search()`; delete `refresh`/`onRefresh`/`job`/`want`/`SEARCH_DELAY_MS`/`Search`/pending;
  simplify `mention()`; trim imports.
- `frontend/.../session/ui/prompt/PromptPanel.kt` — delete `onRefresh` wiring and
  `restartCompletion()`; add `LookupManagerListener` for `ONLY_ABOVE`; simplify
  `showCompletion()`; adjust imports.
- `frontend/src/main/resources/messages/KiloBundle.properties` — remove
  `prompt.mention.searching`.
- `frontend/src/test/.../prompt/KiloPromptCompletionProviderTest.kt` /
  `.../session/ui/PromptPanelTest.kt` — keep existing green; add position test.

## Verification

From `packages/kilo-jetbrains/`:

- `./gradlew :frontend:test --tests
  ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest --tests
  ai.kilocode.client.session.ui.PromptPanelTest`
- `./gradlew typecheck`
- Manual (`./gradlew runIde`): open a session, `@` then type `@backend` **fast** → lookup
  stays open and narrows (no disappear); it opens **above** the caret with no below-flash;
  `/` commands behave the same; send a prompt and repeat.

## Decisions / tradeoffs / risks

1. **Block on the background completion thread; drop the manual restart.** This is the
   idiomatic platform pattern and removes the `onRefresh → scheduleRestart` race that
   causes the disappear. It also removes our 120 ms debounce and the dual code paths
   (test vs prod), unifying behavior.
2. **Chatty-RPC tradeoff.** Each prefix change re-runs the search. In practice superseded
   calculations are cancelled by the platform (`runBlockingCancellable` throws on cancel,
   `searchFiles` rethrows `CancellationException`), the per-prefix `cache` dedupes repeats,
   and `prewarm` covers the empty prefix. Backend file search is cheap, so this is
   acceptable and matches how index-backed completion behaves.
3. **Read lock held during the search.** The contributor runs inside a cancellable read
   action; a localhost search blocks it briefly. `BgCalculation.restartOnWriteAction`
   (`CompletionPhase.kt:446-470`) cancels the indicator the moment the user types (write
   action), promptly releasing the lock. This is the same contract index-backed
   contributors rely on.
4. **Indexing/transient results.** With no auto-refresh, if the backend reports `indexing`
   the user sees the "Indexing…" message until the next keystroke or reopen re-queries
   (rare, brief). `prewarm` already avoids pinning indexing/empty results in `cache[""]`.
5. **Experimental/internal APIs.** `LookupPresentation` / `LookupPositionStrategy` are
   `@ApiStatus.Experimental` and `LookupImpl` is impl-level — already used here. The new
   `LookupManagerListener` is a stable public topic. Revisit on platform upgrades (the
   existing `showCompletion`/`restartCompletion` comments already flag this).
