# Fix JetBrains empty `@` mention completion (shows only "Searching files…")

Scope: Kilo-owned frontend code under
`packages/kilo-jetbrains/frontend/.../session/ui/prompt/`. No shared/upstream files, so
no `kilocode_change` markers needed.

## Symptom (from user)

- Type `@` with no further typing → the lookup shows only `Searching files…` and never
  fills with the root file/dir entries.
- Typing past `@` (e.g. `@backend`) works and shows results. "The rest works ok."
- Doing `@` a **second time** shows entries.

## Root cause

`KiloPromptCompletionProvider.mention("")` (`KiloPromptCompletionProvider.kt:165-188`)
runs a **non-blocking** search via `search("")` (`:190-199`):

- `cache[""]` is empty on the first `@`, so it launches a debounced background fetch
  (`refresh()`, `:207-220`) and renders the `Searching files…` placeholder.
- When the fetch returns it stores `cache[""]` and posts `onRefresh()` →
  `PromptPanel.restartCompletion()` (`PromptPanel.kt:561-569`) which calls
  `CompletionServiceImpl.currentCompletionProgressIndicator?.scheduleRestart()` to repaint
  the open lookup in place.

The **second** `@` works because `cache[""]` is now populated, so `search("")` returns
synchronously (`:191`) and the lookup is filled on first paint with no async restart.

So the failing case is exactly the one that depends on the async
`onRefresh → restartCompletion → scheduleRestart()` repaint of an already-open lookup,
and that in-place repaint is the fragile/internal-API path. Typing past `@` does **not**
depend on it: each keystroke routes through the platform's own
`LookupTypedHandler.prefixUpdated()` → `scheduleRestart()`
(`$INTELLIJ_REPO/platform/lang-impl/.../lookup/impl/LookupTypedHandler.java:176-179`),
which re-runs `fillCompletionVariants` and warms/serves the cache reliably. The empty `@`
is the only path with no keystroke to drive that robust restart.

### Investigation note (why we are not relying on a "race" theory)

`@` is not a letter/digit/`_`, so `CompletionAutoPopupHandler.checkAutoPopup` returns
CONTINUE for it (`.../editorActions/CompletionAutoPopupHandler.java:43-50`) and
`TypedAutoPopupImpl` only auto-pops on `.`/`/`/contributor opt-in
(`.../editorActions/TypedAutoPopupImpl.java:30-37`). Typing `@` therefore never triggers
the platform autopopup; the popup is opened solely by our explicit `showCompletion`
(`PromptPanel.kt:551-559`, `invokeCompletion(project, ed, 1)` → `invocationCount == 1` →
`isAutopopupCompletion() == false`, `CompletionProgressIndicator.java:842-844`). So the
empty-`@` failure is **not** an autopopup-vs-explicit race on `@`. Removing
`ALWAYS_AUTO_POPUP` is correct hygiene but is not, by itself, a guaranteed fix — so we add
a deterministic cache prewarm.

## Plan

### Part 1 — Prewarm `cache[""]` (deterministic fix)

Make the first `@` take the same path that already works the second time: serve the root
listing synchronously from a warm cache.

**1a. Add `prewarm()` to `KiloPromptCompletionProvider`.**

```kotlin
fun prewarm() {
    if (cache.containsKey("")) return
    scope.launch {
        val result = service.searchFiles(workspace.directory, "", 50)
        // Only pin a settled, useful result. Skip empty/failed/indexing results so a
        // later `@` retries instead of caching a transient "nothing" state.
        if (result.files.isNotEmpty() || result.git) cache.putIfAbsent("", result)
    }
}
```

- Launches on the existing `scope` (off-EDT, satisfies `searchFiles`' not-EDT
  requirement). No unit-test-mode early return, so it is directly testable.
- `cache[prefix]` is checked before everything else in `search()`
  (`KiloPromptCompletionProvider.kt:191`), so once `cache[""]` is warm the first `@`
  renders root entries on first paint with no placeholder and no async restart.
- Does not pin `indexing`/empty/failed results (`searchFiles` returns
  `FileSearchResultDto()` on failure), avoiding a stuck "No matches"/empty cache.

**1b. Re-warm at the right lifecycle points in `PromptPanel`.**

- In `setReady(true)` (`PromptPanel.kt:304-308`): call `completion?.prewarm()` — the
  workspace is ready so the backend can search. This warms the cache before the user's
  first `@`.
- In `clear()` (`PromptPanel.kt:383-391`), after `completion?.clearMentions()` (which
  empties the cache, `:68-76`): call `completion?.prewarm()` so the next `@` after a send
  is warm again.

`prewarm()` is idempotent (`containsKey("")` guard), so repeated `setReady(true)` /
`clear()` calls are cheap.

### Part 2 — Remove `ALWAYS_AUTO_POPUP` (requested restructure)

Drive completion solely through the explicit triggers we already own and the active
lookup's own prefix-change restart, so every completion in a session is explicit
(`invocationCount == 1`, non-autopopup) and `scheduleRestart()` always takes the
non-autopopup async path.

**2a. Remove** `ed.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, true)`
(`PromptPanel.kt:172`) and the now-unused `import com.intellij.codeInsight.AutoPopupController`
(`:23`).

Why this is safe (verified against `$INTELLIJ_REPO`):
- Opening: `triggerCompletion` opens the popup on a single-char `@` (anywhere) and a
  leading `/` (`PromptPanel.kt:537-549`); the bound completion shortcut also calls
  `showCompletion` (`installCompletionShortcut`, `:571-584`).
- Narrowing while open: typing routes through `LookupTypedHandler.prefixUpdated()` →
  `scheduleRestart()` and `restartCompletionOnAnyPrefixChange()`
  (`KiloPromptCompletionProvider.kt:152,166`) — independent of `ALWAYS_AUTO_POPUP`.
- All completions stay non-autopopup: `showCompletion` uses `invocationCount = 1`, and
  restarts inherit it (`phase.indicator?.invocationCount`,
  `.../completion/CompletionPhase.kt:339`).
- Net effect: removes wasted autopopup completion attempts on ordinary (non-`@`/`/`)
  typing and guarantees the async `scheduleRestart()` never routes through the gated
  autopopup path (`shouldSkipAutoPopup` / activity guards, `CompletionPhase.kt:285,326-333`).

No behavior the tests depend on changes: `KiloPromptCompletionProviderTest` and
`PromptPanelTest` drive completion via `myFixture.completeBasic()` / the explicit
"Kilo Prompt Completion" action (`invokeCompletionAction`), neither of which uses
`ALWAYS_AUTO_POPUP`.

## Tests

- `KiloPromptCompletionProviderTest` (provider constructed directly, no panel → `prewarm`
  is only called explicitly by the test, so `searchQueries` assertions in other tests are
  unaffected). Add:
  - **prewarm warms the empty-prefix cache:** set `rpc.searchResult` with a couple of root
    entries, call `provider.prewarm()`, `waitFor { rpc.searchQueries.contains("") }`, then
    `complete("@<caret>")` and assert the lookup contains those entries **and**
    `assertEquals(listOf(""), rpc.searchQueries)` (the `@` was served from the prewarmed
    cache, no second query).
  - Existing `test blank mention completion includes special and root entries` and the
    `searchQueries` expectations stay green (no prewarm there).
- `PromptPanelTest`: existing completion/highlight tests must stay green. `setReady(true)`
  / `clear()` already appear in many tests; confirm none assert `searchQueries` (they do
  not) so the added prewarm fetch is harmless. Optionally add a panel-level check that the
  prewarm fetch is issued after `setReady(true)`.

## Files to change

- `frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt` — add `prewarm()`.
- `frontend/.../session/ui/prompt/PromptPanel.kt` — call `prewarm()` from `setReady(true)`
  and `clear()`; remove `ALWAYS_AUTO_POPUP` and its import.
- `frontend/src/test/.../prompt/KiloPromptCompletionProviderTest.kt` — prewarm test.

## Verification

From `packages/kilo-jetbrains/`: `./gradlew :frontend:test` (targeted to the two prompt
test classes) and `./gradlew typecheck` (Java 21). Manual check in `./gradlew runIde`:
open a session, type `@` and wait — root files/dirs appear immediately (no stuck
`Searching files…`); type `@backend` — narrows correctly; send a prompt, type `@` again —
still immediate.

## Decisions / tradeoffs

1. **Prewarm is the primary, deterministic fix.** It reproduces the working "second time"
   path on the first `@` and does not depend on the fragile in-place async restart.
2. **`ALWAYS_AUTO_POPUP` removal is included as requested** and is safe hygiene, but the
   investigation shows `@` does not go through the platform autopopup, so it is paired with
   prewarm rather than relied on alone.
3. Only settled, useful (`files.isNotEmpty() || git`) results are pinned in `cache[""]` to
   avoid caching transient empty/indexing/failed states.
