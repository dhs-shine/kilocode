# JetBrains Mention Icons And Priority Plan

## Goal

- Show IntelliJ file type icons for file mention completions instead of the generic text icon.
- Keep predefined mention completions, currently `@git-changes`, above file/folder entries in the blank `@` default list.

## Current State

- `KiloPromptCompletionProvider.file(...)` uses `AllIcons.Nodes.Folder` for directories and `AllIcons.FileTypes.Text` for every non-directory file.
- `AttachmentOpeners.attachmentIcon(...)` already uses `FileTypeManager.getInstance().getFileTypeByFileName(name).icon ?: AllIcons.FileTypes.Text`, which is a suitable public IntelliJ API pattern to reuse.
- `mention(...)` adds `git-changes` before files, but the completion sorter can still influence presentation. Explicit priority will make the intended ordering stable.
- The backend/RPC DTO already provides `WorkspaceFileDto.name` and `directory`, so no shared DTO or backend changes are needed.

## Implementation

1. Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProvider.kt`.
2. Import `com.intellij.openapi.fileTypes.FileTypeManager` and `com.intellij.codeInsight.lookup.PrioritizedLookupElement`.
3. Add a small helper for mention file icons:
   - Return `AllIcons.Nodes.Folder` when `file.directory` is true.
   - Otherwise return `FileTypeManager.getInstance().getFileTypeByFileName(file.name).icon ?: AllIcons.FileTypes.Text`.
   - Use `file.name`, not `file.path`, so file type lookup uses the final path segment consistently.
4. Change `file(file)` to call the icon helper instead of hardcoding `AllIcons.FileTypes.Text`.
5. Add a small helper for predefined mention priority:
   - Wrap predefined mention lookup elements with `PrioritizedLookupElement.withPriority(..., 100.0)`.
   - Keep file lookup elements unprioritized.
   - This should apply to `@git-changes` whenever it is included, including the blank `@` list and non-empty matching prefixes.
6. Keep the existing backend search behavior unchanged, including root folder/file listing for blank queries and fuzzy search for non-empty queries.

## Tests

Update `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProviderTest.kt`.

1. Extend the blank mention completion test to assert ordering:
   - `lookupElementStrings.orEmpty().take(3)` should be `listOf("git-changes", "src", "README.md")` when the fake backend returns `git = true` and root entries.
2. Add coverage for file type icons:
   - Complete a file with a recognizable extension, e.g. `image.png`.
   - Render the corresponding lookup element into `LookupElementPresentation`.
   - Assert the icon equals `FileTypeManager.getInstance().getFileTypeByFileName("image.png").icon ?: AllIcons.FileTypes.Text`.
3. Keep or add directory icon coverage if cheap:
   - Assert a directory entry renders `AllIcons.Nodes.Folder`.

## Verification

Run from `packages/kilo-jetbrains/`:

```bash
./gradlew :frontend:test --tests ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest
./gradlew typecheck
```

## Release Note

- This is user-facing JetBrains behavior. Add a patch changeset such as `.changeset/<slug>.md` for `@kilocode/kilo-jetbrains` with a concise user-facing description, for example: `Show file type icons and keep predefined mentions first in JetBrains mention completions.`

## Risks

- File type icons depend on installed IntelliJ file type registrations. Tests should compare against the platform API result instead of assuming a specific concrete icon for an extension.
- Completion ordering can be affected by IntelliJ sorters. Use `PrioritizedLookupElement` for predefined mentions instead of relying only on insertion order.
