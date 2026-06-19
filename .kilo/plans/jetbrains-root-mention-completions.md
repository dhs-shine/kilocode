# JetBrains Root Mention Completions

## Goal

When a JetBrains user types `@` in the prompt with no query, show useful file/folder suggestions from the workspace root instead of only `@git-changes`, matching the VS Code behavior more closely.

## Current Findings

- Frontend completion is implemented in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProvider.kt`.
- `mention(prefix, result)` calls `search(prefix)` for all mention completions. With plain `@`, `prefix == ""`.
- The backend RPC is `KiloWorkspaceRpcApiImpl.searchFiles(...)` in `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloWorkspaceRpcApiImpl.kt`.
- Backend `search(project, base, query, limit)` currently returns `emptyList()` when `query.trim().isBlank()`, so the frontend only has special mentions such as `@git-changes`.
- VS Code file mentions request both file and folder search and merge file/folder/open-editor results in `packages/kilo-vscode/src/kilo-provider/file-search.ts`; for the JetBrains fix, the smallest useful parity step is root-directory prepopulation for blank queries.

## Implementation Plan

1. Keep the existing fuzzy search path unchanged for non-empty mention queries.
   - Leave the current `GotoFileModel` / `ChooseByName*` code in `KiloWorkspaceRpcApiImpl.search(...)` intact for typed queries such as `@main` or `@src/foo`.
   - Only branch when `query.trim().isBlank()`.

2. Add a blank-query root listing path in the backend.
   - In `KiloWorkspaceRpcApiImpl.search(...)`, replace `if (text.isBlank()) return emptyList()` with a call to a new private helper, for example `roots(project, base, limit)`.
   - Resolve `base` through `LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base)`.
   - Read immediate children only, not a recursive tree, to keep `@` completion fast and predictable.
   - Convert each child through existing `fileDto(base, vf)` so returned paths remain relative and normalized the same way as fuzzy results.

3. Filter root entries to avoid noisy or invalid suggestions.
   - Exclude `.git` explicitly.
   - Use IntelliJ project indexing APIs such as `ProjectFileIndex.getInstance(project)` to skip excluded files/directories when available.
   - Preserve directories by returning `WorkspaceFileDto(..., directory = true)` via `fileDto` so the existing frontend folder icon path continues to work.

4. Sort root suggestions for a useful default list.
   - Put directories before files, matching common file picker behavior.
   - Sort each group case-insensitively by name.
   - Respect the existing `limit` after sorting.
   - Do not pin or special-case repository-specific names unless later UX feedback asks for it.

5. Keep special mention behavior unchanged.
   - `@git-changes` should still appear when `search.git` is true and the prefix matches, including blank prefix.
   - The frontend should continue to add special mentions before file suggestions, so git changes remains at the top when available.

6. Add focused frontend completion coverage.
   - Update `KiloPromptCompletionProviderTest` with a test for `@<caret>` where the fake RPC returns root entries plus `git = true`.
   - Assert the lookup contains `git-changes` and returned file/folder paths.
   - Assert the RPC saw a blank query (`listOf("")`) so the behavior is explicitly covered.
   - If needed, extend the existing `file(...)` helper to create directory DTOs for icon/type coverage, but keep the test focused on visible completion values.

7. Add backend coverage if practical in the existing test harness.
   - First look for an existing IntelliJ project fixture pattern in backend tests.
   - If lightweight backend fixture setup is practical, add a test around `searchFiles(directory, "", limit)` that creates root children and verifies immediate relative entries only.
   - If backend fixture setup is heavy or brittle, rely on the frontend completion test plus `typecheck`, and note the backend behavior is exercised manually in sandbox.

8. Verification.
   - Run the smallest relevant JetBrains checks from `packages/kilo-jetbrains/`.
   - Start with the targeted frontend test class, e.g. `./gradlew :frontend:test --tests ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest` if the module task supports it.
   - Run `./gradlew typecheck` from `packages/kilo-jetbrains/` after tests pass.
   - Do not run Java preflight unless Gradle reports a Java version or missing-Java failure.

## Non-Goals

- Do not change VS Code mention behavior.
- Do not introduce recursive blank-query search; initial `@` should show root-level entries only.
- Do not change how selected file mentions are serialized into prompt parts.
- Do not change rendering of mentioned files or synthetic file content; that is covered by the separate `jetbrains-file-mention-parity.md` plan.
- Do not modify shared OpenCode files; this work stays inside the Kilo JetBrains package.
