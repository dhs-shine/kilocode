# Fix JetBrains prompt mention completion + highlighting

Scope: Kilo-owned frontend code under
`packages/kilo-jetbrains/frontend/.../session/ui/prompt/`. No shared/upstream files,
so no `kilocode_change` markers needed.

## Symptoms (from user)

Completion:
- Type `@` then quickly `ba` → the completion popup **disappears**.
- Typing slowly works, but there is a visible delay/rebuild between keystrokes.

Highlighting:
- After accepting a file from completion, the mention is **not** highlighted as valid
  until you type a space.
- Editing the tail of a valid mention so it becomes invalid does **not** turn it red.

User goals: less blinking on rebuilds, do not hide the popup while typing between
rebuilds, correct path validation.

## Root cause analysis

### Why the popup disappears (completion)

`KiloPromptCompletionProvider.mention()` calls `search(prefix)` which runs the backend
file search **synchronously** via `runBlockingCancellable` inside
`fillCompletionVariants` (`KiloPromptCompletionProvider.kt:166-171`). It also calls
`result.restartCompletionOnAnyPrefixChange()`, so every typed character restarts the
whole completion.

IntelliJ's restart path (`CompletionPhase.scheduleAsyncCompletion` →
`startAsyncCompletionIfNotExpired`, verified in
`$INTELLIJ_REPO/platform/lang-impl/.../CompletionPhase.kt:326-333`) cancels a scheduled
completion when **activity happened in the editor AND the previous lookup is not yet
shown**:

```
if (phase.myTracker.hasAnythingHappened() && (phase.indicator == null || !phase.indicator.lookup.isShown)) {
  phase.cancelPhase(); setCompletionPhase(NoCompletion); return // popup gone, not rescheduled
}
```

So while the first blocking `search()` is still running (lookup not shown yet), typing a
second character cancels the session entirely and nothing reopens it. Slow typing leaves
enough time for the blocking search to finish and the lookup to show; once a lookup is
shown, later restarts are kill-safe (`!lookup.isShown` is false). This exactly matches
"fast = disappears, slow = works". The blocking RPC is the core problem.

The pattern (blocking + `restartCompletionOnAnyPrefixChange`) is what LSP uses
(`LspCompletionContributor.kt:44-48`), but LSP also suffers the same issue for slow
servers. We must make the **first paint non-blocking** so the lookup shows immediately.

### Why blinking happens on rebuilds

Each keystroke fully restarts completion, re-queries the backend (blocking), and tears
down/rebuilds the item list — even though we already have usable results for a shorter
prefix. Empty/"No matches" can flash mid-refresh.

### Why highlighting is wrong

Highlights only refresh on **document change** (`PromptPanel` document listener →
`syncHighlights()`, `PromptPanel.kt:241-249,377-403`). Two consequences:

1. **Not valid after accept:** In `replace()` (`KiloPromptCompletionProvider.kt:228-241`)
   the path is added to `paths`/`exists` **after** `ctx.document.replaceString(...)`. The
   synchronous `documentChanged` → `syncHighlights()` therefore runs while `paths` is
   still empty, so the just-inserted mention is not highlighted. The next edit (typing a
   space) re-syncs and it finally turns valid.
2. **Doesn't turn red after editing tail:** `highlights()` suppresses any mention while
   the caret is inside it (`under -> Unit`, `KiloPromptCompletionProvider.kt:84-90`), and
   `validate()` skips tokens under the caret (`:99`). When the caret leaves the token via
   click/arrow (no document change), nothing re-syncs, so validation never runs and the
   now-invalid mention is never flagged. Typing a space forces a re-sync, which is the
   only reason it eventually works.

## Plan

### Part A — Highlighting (low risk, do first)

**A1. Valid immediately after accepting a completion.**
In `KiloPromptCompletionProvider.replace()`, move the `paths.add(path)` /
`exists[path] = true` update to **before** `ctx.document.replaceString(...)`. The
synchronous document-change → `syncHighlights()` then sees the new mention and highlights
it as `MENTION` right away. No behavior change to final text or `mentionPaths()`, so
existing `replace`-related tests still pass.

**A2. Re-validate when the caret leaves a mention token.**
Add a `CaretListener` to the prompt editor (in `PromptPanel`'s `addSettingsProvider`
block alongside the existing focus listener, `PromptPanel.kt:178-186`) that calls
`syncHighlights()` when the caret crosses a mention-span boundary. Guard it so it only
re-syncs when the set of "caret is inside a mention" actually changes (compare previous
vs new offset against `mentionSpans`) to avoid churn on ordinary cursor movement. This
makes an edited-then-left mention run `validate()` and turn red (or back to valid).
Register on the per-editor `caretModel`; it is released with the editor, consistent with
the existing focus listener.

### Part B — Completion popup (the disappearing/blinking fix)

Make mention completion **non-blocking** and serve results from a cache, refreshing in
the background. Slash completion is already synchronous/in-memory and is left unchanged.

**B1. Replace the single `cached` field with a small cache + async machinery** in
`KiloPromptCompletionProvider`:
- `private val cache = ConcurrentHashMap<String, FileSearchResultDto>()` (thread-safe;
  `fillCompletionVariants` runs on a background completion thread, the refresh writes from
  a coroutine).
- `@Volatile private var job: Job? = null` (single in-flight fetch; cancel on new
  request — debounced).
- `@Volatile private var want: String? = null` (latest requested prefix, to ignore stale
  fetch completions).
- `var onRefresh: (() -> Unit)? = null` — set by `PromptPanel` to restart the active
  completion when fresh data arrives.
- `clearMentions()` clears `cache`, cancels `job`, resets `want` (keeps existing reset
  semantics so the "clearing resets cached prefix result" test still does two queries).

**B2. Non-blocking `mention(prefix, result)`** decision order:
1. `cache[prefix] != null` → render those results (exact, instant).
2. Else pick the **longest cached key that is a prefix of `prefix`** as a stale base and
   render its results with the existing `PlainPrefixMatcher.ALWAYS_TRUE` (instant, keeps
   the lookup populated and shown); then request a background refresh for `prefix`.
3. Else (truly cold, no base) → render a "searching" placeholder via the existing
   `info()` element (`NEVER_AUTOCOMPLETE`, prefix-preserving) so the lookup still shows;
   request a background refresh.

Because fill never blocks, the lookup always shows on the first paint → the platform's
"activity happened & lookup not shown" kill path can no longer fire → the popup stops
disappearing on fast typing. Keep `restartCompletionOnAnyPrefixChange()` so each
keystroke re-runs this cheap fill (re-evaluates cache/stale and kicks the next refresh).

**B3. Background refresh (debounced) + in-place restart.**
- `refresh(prefix)`: set `want = prefix`; cancel previous `job`; launch on `scope`:
  optional small `delay(~120ms)` debounce, then `service.searchFiles(dir, prefix, 50)`,
  store in `cache[prefix]`, and if `prefix == want` post `onRefresh()` on the EDT.
- `PromptPanel` sets `completion.onRefresh = { restartActiveCompletion() }` where
  `restartActiveCompletion()` is `@RequiresEdt`, checks `!project.isDisposed`, and — only
  if a lookup is active on this editor — restarts it **in place** (replaces items without
  closing/reopening) using the platform completion indicator. This is the existing
  documented "uses IntelliJ impl/internal completion APIs; revisit on platform upgrades"
  boundary already present for `showCompletion` (`PromptPanel.kt:533-541`); reuse the same
  caveat comment. Guard to the current editor (`indicator.editor === ed`).

This yields: instant first paint, stale results held across restarts (no empty/"No
matches" flash → less blinking), one debounced backend query per settled prefix, and an
in-place refresh when results arrive.

**B4. Unit-test mode stays synchronous.**
`fillCompletionVariants`/`mention` is invoked through `myFixture.completeBasic()` in the
provider tests, which expect results synchronously. When
`ApplicationManager.getApplication().isUnitTestMode()` is true, perform the cold fetch
inline (`runBlockingCancellable`, current behavior) and skip the async
refresh/`onRefresh` path. This keeps every existing `KiloPromptCompletionProviderTest`
and `PromptPanelTest` completion test green with no rewrites, while production uses the
async path. (`isUnitTestMode()` branching is the platform's own convention in completion
code, e.g. `scheduleAutoPopup`/`handleEmptyLookup`.) Documented as a deliberate tradeoff
in the open questions.

**B5. Optional refinement — avoid double-trigger blink.**
`@`/`/` triggers an explicit `showCompletion` (`PromptPanel.triggerCompletion`/
`showCompletion`) while the editor also has `AutoPopupController.ALWAYS_AUTO_POPUP`, so
two completions can race on the first character. Guard `showCompletion` to no-op if a
lookup is already active for the editor. Keep as a follow-up tweak; the main fix is B1–B4.

### New bundle key

Add `prompt.mention.searching=Searching files…` to
`frontend/src/main/resources/messages/KiloBundle.properties` (English only; other locales
fall back). Used by the cold "searching" placeholder.

## Tests

- `KiloPromptCompletionProviderTest`: unchanged and must stay green (B4 keeps them
  synchronous). Add:
  - cache base reuse: after `complete("@back")` then `complete("@backend")`, the second
    serves stale base results immediately (assert no regression in `searchQueries`
    expectations as designed).
- `PromptPanelTest`:
  - **Accept → valid immediately (A1):** type `@dep`, open lookup, accept `src/deploy.ts`,
    and assert the mention span has `DefaultLanguageHighlighterColors.METADATA`
    **without** typing a space (use `waitForSend`).
  - **Edit tail → invalid on caret leave (A2):** set a valid mention (`fileResolver`
    returns it), edit the tail to an unknown path, move the caret out of the token, and
    assert `CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES` appears via `waitForSend`.
  - Existing highlight/lookup tests must remain green.

## Files to change

- `frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt` — A1 reorder; B1–B4
  cache/async/refresh; cold placeholder.
- `frontend/.../session/ui/prompt/PromptPanel.kt` — A2 caret listener; wire
  `completion.onRefresh` + `restartActiveCompletion()`; optional B5 guard.
- `frontend/src/main/resources/messages/KiloBundle.properties` — `prompt.mention.searching`.
- Tests: `frontend/src/test/.../prompt/KiloPromptCompletionProviderTest.kt`,
  `frontend/src/test/.../ui/PromptPanelTest.kt`.

## Verification

From `packages/kilo-jetbrains/`: `./gradlew typecheck` and `./gradlew test` (Java 21).
Manual check in `./gradlew runIde`: type `@` then quickly `ba` (popup stays, narrows to
backend entries); accept a file (highlights valid immediately); break a valid mention's
tail and click away (turns red).

## Decisions

1. **In-place restart (B3):** use the platform completion indicator (the same internal
   boundary already used by `showCompletion`, with the existing "revisit on platform
   upgrades" caveat comment). Lowest blink; replaces items without closing the lookup.
2. **Tests (B4):** branch on `ApplicationManager.getApplication().isUnitTestMode()` so the
   cold fetch runs inline in tests (existing tests stay green, no rewrites); production
   uses the async path.

## Remaining tunable

- **Debounce delay** for the background refresh: starting at ~120ms; tune during
  implementation/manual testing.
