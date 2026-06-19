# Fix JetBrains Mention File Search Refresh

## Diagnosis

`@deploy.sh` can fail even when IntelliJ Find by Name can locate the file because mention completion currently performs backend file search only once, at the moment completion is invoked.

Current flow:

- Typing `@` explicitly invokes completion in `PromptPanel`.
- `KiloPromptCompletionProvider.mention()` receives an empty prefix and calls `searchFiles(workspace.directory, "", 50)`.
- The backend returns the first limited slice of indexed file names, not all repo files.
- As the user types `deploy.sh`, `acceptChar()` returns `ADD_TO_PREFIX`, so IntelliJ appends the typed chars to the active lookup prefix and filters the already-returned lookup items.
- The backend is not queried again unless completion is restarted.
- If `deploy.sh` was not in the initial empty-prefix result slice, it can never appear.

Secondary backend issue:

- `KiloWorkspaceRpcApiImpl.search()` uses `NameUtil.buildMatcher("*${query.trim()}")`.
- This behaves like a suffix-ish matcher rather than a broad contains/fuzzy search for partial file names.
- A partial query like `deploy` may not match `deploy.sh` until the extension is typed, depending on matcher semantics.

Platform finding:

- IntelliJ exposes public restart hooks on `CompletionResultSet`: `restartCompletionOnPrefixChange(...)`, `restartCompletionOnAnyPrefixChange()`, and `restartCompletionWhenNothingMatches()`.
- For `TextCompletionProvider`, these can be called from `fillCompletionVariants(...)`; no internal completion APIs are needed.
- Relevant IntelliJ source:
  - `TextCompletionContributor.java`: computes prefix and delegates to the provider.
  - `TextCompletionCharFilter.java`: `ADD_TO_PREFIX` keeps the lookup alive and updates prefix.
  - `LookupTypedHandler.java`: typed chars only restart completion when requested by result-set restart hooks.
  - `CompletionResultSet.java`: public restart methods.

## Implementation Plan

1. Update `KiloPromptCompletionProvider.mention()` to request backend re-query as the mention prefix changes.
   - Prefer `result.restartCompletionOnAnyPrefixChange()` for file mentions, or `restartCompletionOnPrefixChange(StandardPatterns.string().longerThan(0))` if empty-prefix churn is undesirable.
   - Also call `result.restartCompletionWhenNothingMatches()` as a fallback so missed initial slices recover as the user narrows the prefix.
   - Keep slash command behavior static unless slash commands show a similar issue; slash options are small enough to filter locally.

2. Improve backend filename matching in `KiloWorkspaceRpcApiImpl.search()`.
   - Use a contains/fuzzy pattern such as `*${query.trim()}*` instead of `*${query.trim()}`.
   - Treat blank query separately to avoid pathological wildcard behavior and keep initial `@` results capped.
   - Continue sorting by matching degree, path length, then path.

3. Add a targeted test if practical in the JetBrains test suite.
   - Unit-test the provider behavior only if existing tests can invoke text completion without excessive platform setup.
   - Otherwise, add coverage around backend search matching if there is already a backend/RPC test harness.
   - Avoid mocks where possible; use actual implementation helpers if accessible.

4. Verify.
   - Run `./gradlew typecheck` from `packages/kilo-jetbrains/`.
   - If tests are added, run the smallest relevant Gradle test task for the touched module.

## Expected Result

Typing `@deploy.sh` should re-run backend search as the prefix changes, so indexed repo files like `deploy.sh` appear even when they were not included in the initial empty-`@` result set.

## Notes

- The file still must be under the current workspace directory and visible to the IntelliJ project index.
- If standard IntelliJ Find by Name sees the file and it is under the workspace root, mention completion should see it after this fix.
