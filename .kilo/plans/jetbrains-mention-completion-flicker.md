# Fix flickering `@`-mention / `/`-slash prompt completion (JetBrains)

## Goal

Stop the prompt completion lookup from flickering as the user types an `@`-mention,
without changing the inline-dropdown UX. Keep reusing IntelliJ's standard completion
Lookup and the backend `GotoFileModel` ("Go to File") search engine that already power
the feature. This is **Option 1** from the investigation (smallest change, fully
Kilo-owned code, no UX change).

## Background / root cause

All touched code lives under Kilo-owned paths (`ai/kilocode/client/...`), so **no
`kilocode_change` markers are needed**.

Current flow:

- `PromptPanel` editor uses `TextCompletionUtil.DocumentWithCompletionCreator(provider, autoPopup=true)`.
- `PromptPanel.triggerCompletion()` manually invokes completion when `@` or a leading `/` is typed
  (`PromptPanel.kt:457-478`), forcing `LookupPositionStrategy.ONLY_ABOVE`.
- `KiloPromptCompletionProvider.mention()` (`KiloPromptCompletionProvider.kt:73-93`) runs a
  **blocking RPC search per completion pass** (`runBlockingCancellable { service.searchFiles(...) }`)
  and applies a local `PlainPrefixMatcher(prefix)`.
- Backend `KiloWorkspaceRpcApiImpl.search()` (`KiloWorkspaceRpcApiImpl.kt:317-367`) uses IntelliJ's
  `GotoFileModel` for fuzzy/camel-hump ranking.

Why it flickers (collapse → re-query → refill cycle every keystroke):

1. Backend does fuzzy/camel matching; the frontend re-filters with `PlainPrefixMatcher`
   (substring match), which **hides server results that don't literally contain the typed
   substring** (e.g. `sfb` → `src/foo/Bar.kt`).
2. A blank query returns `emptyList()` from the backend, so right after `@` only the
   git-changes/terminal specials show; the first typed char tends to empty the locally
   filtered list.
3. `restartCompletionWhenNothingMatches()` only re-queries **after** the list is already
   empty, so the user sees empty → (async RPC gap) → refill on nearly every keystroke.
4. Variable RPC latency makes the refill land at inconsistent times.

## The change (precise edits)

### 1. `KiloPromptCompletionProvider.mention()` — let the server own filtering

File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProvider.kt`

- Replace `result.restartCompletionWhenNothingMatches()` with
  `result.restartCompletionOnAnyPrefixChange()` so the platform keeps the lookup open and
  deterministically re-runs the query on every prefix change (replacing items in place)
  instead of waiting for the list to empty.
- Use an **accept-everything matcher** for the mention path so the platform never hides the
  server's ranked results: wrap with `PlainPrefixMatcher.ALWAYS_TRUE`
  (`com.intellij.codeInsight.completion.PlainPrefixMatcher`) instead of
  `applyPrefixMatcher(result, prefix)`.

Sketch:

```kotlin
private fun mention(prefix: String, result: CompletionResultSet) {
    result.restartCompletionOnAnyPrefixChange()
    val out = result.withPrefixMatcher(PlainPrefixMatcher.ALWAYS_TRUE)
    val search = searchFiles(prefix)        // see step 2 (cache)
    // ...unchanged: specials (git-changes, terminal), indexing advertisement, files...
}
```

Notes:

- Keep `applyPrefixMatcher` (the `TextCompletionProvider` override) as-is — it is still used
  by the `slash` path, where a static command list should be filtered locally. Only the
  mention path overrides the matcher to accept-all. `TextCompletionContributor` calls
  `applyPrefixMatcher` before `fillCompletionVariants`, so re-wrapping inside `mention`
  with `ALWAYS_TRUE` is the correct seam.
- Items are added in server-weight order; with `ALWAYS_TRUE` there is no client-side
  highlight/filter, which is intended (server is the source of truth).

### 2. Small last-prefix cache to cut redundant RPC (optional but recommended)

Still in `KiloPromptCompletionProvider`. With `restartCompletionOnAnyPrefixChange`, the
platform re-invokes the provider on each change and `runBlockingCancellable` cancels stale
passes. Add a **1-entry cache** keyed by `prefix` so identical prefixes (e.g. delete +
retype, or a duplicate pass for the same prefix) don't re-hit RPC:

```kotlin
@Volatile private var cached: Pair<String, FileSearchResultDto>? = null

private fun searchFiles(prefix: String): FileSearchResultDto {
    cached?.takeIf { it.first == prefix }?.let { return it.second }
    val result = runBlockingCancellable { service.searchFiles(workspace.directory, prefix, 50) }
    cached = prefix to result
    return result
}
```

- Clear `cached` in `clearMentions()` (called from `PromptPanel.clear()`) to avoid stale
  results across prompt resets.
- Keep it a single entry; do not build an unbounded map.

### 3. Verify the `@` trigger does not double-fire

File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptPanel.kt`

- `autoPopup=true` plus the manual `triggerCompletion()` could both fire on the first `@`.
  During implementation, confirm (via `runIde`) whether typing `@` briefly shows a double
  session. If it does, prefer keeping the **manual** trigger (autopopup is unreliable for
  non-identifier chars like `@`) and confirm the second `scheduleAutoPopup` is a no-op while
  a lookup is already active. Only adjust if a real double-flash is observed — otherwise
  leave `triggerCompletion`/`showCompletion` and the `ONLY_ABOVE` strategy unchanged.
- Keep `LookupPositionStrategy.ONLY_ABOVE` (added in `7fd80d0af9`) — it prevents the popup
  from flipping above/below as item counts change.

## Edge cases / considerations

- **Empty prefix right after `@`**: backend returns no files (blank query), so only
  git-changes/terminal specials show until the user types. This is acceptable and not part
  of the flicker bug; showing recent files for an empty prefix is a separate enhancement and
  is **out of scope**.
- **Indexing state**: the existing `search.indexing` branch (advertisement + single info
  element, early return) is unchanged.
- **Slash path**: unchanged. It must keep local `PlainPrefixMatcher` filtering of the static
  command list and must NOT call `restartCompletionOnAnyPrefixChange`.
- **Threading**: `searchFiles` stays a cancellable blocking call inside the completion
  background pass (as today). No EDT work added.

## Testing

Add `KiloPromptCompletionProviderTest` (frontend test, `BasePlatformTestCase`), modeled on
existing `KiloWorkspaceService(scope, FakeWorkspaceRpcApi())` usage
(e.g. `KiloWorkspaceServiceTest`, `SessionUiTestBase`) and `workspaces.workspace("/test")`.

Use `myFixture` to drive the real completion machinery against a plain-text document with the
provider installed via `TextCompletionUtil.installProvider(file, provider, true)`:

- **No local collapse of fuzzy results**: set `FakeWorkspaceRpcApi.searchResult` to files
  whose paths do NOT contain the typed substring (simulating camel/fuzzy matches, e.g.
  query `sfb` returning `src/foo/Bar.kt`). After completing on `@sfb<caret>`, assert all
  server files appear in `myFixture.lookupElementStrings` (proves `ALWAYS_TRUE` matcher).
- **Re-query on prefix change**: extend `FakeWorkspaceRpcApi.searchFiles` to **record the
  query string** (and optionally return per-query results). Assert that changing the prefix
  triggers a new `searchFiles` call for the new prefix (proves
  `restartCompletionOnAnyPrefixChange`).
- **Cache**: assert a repeated identical prefix does not issue a second `searchFiles` call,
  and that `clearMentions()` resets the cache.
- **Specials still surface**: with `searchResult.git = true` / `terminal = true`, assert
  `@git-changes` / `@terminal` appear for matching prefixes.

Test-support change required: add query recording to `FakeWorkspaceRpcApi.searchFiles`
(e.g. `val searchQueries = mutableListOf<String>()` and optionally a
`searchByQuery: (String) -> FileSearchResultDto` override). This is in the `testing` package
and acceptable.

If the full `myFixture.complete` lookup proves too fiddly for deterministic assertions, fall
back to asserting the visible lookup element strings after `myFixture.type(...)` of each
character, which is the user-facing behavior we care about. Do not add production methods
that exist only for test access.

## Verification

- Unit/integration: `./gradlew test` (or targeted `./gradlew test --tests '*KiloPromptCompletionProviderTest*'`)
  from `packages/kilo-jetbrains/`. Requires Java 21 (`java -version`; install via
  `sdk install java 21-tem && sdk use java 21-tem` if missing).
- Typecheck: `./gradlew typecheck` from `packages/kilo-jetbrains/`.
- Manual: `./gradlew runIde`, open the Kilo tool window, type `@` then progressively type a
  fuzzy filename; confirm the lookup stays open and updates in place with no empty→refill
  flash, and that selecting a file inserts `@<path>`.

## Out of scope / fallback

- No switch to the Search Everywhere / Go-to-File chooser (Option 2) and no custom
  `JBPopup` + `JBList` (Option 3). If, after this change, RPC latency still dominates and the
  lookup is not smooth enough, escalate to Option 3 (custom popup with debounced async list
  updates) as a follow-up.
- No changes to backend `search()` / `GotoFileModel` ranking.
- No new behavior for empty-prefix `@` (recent files).
