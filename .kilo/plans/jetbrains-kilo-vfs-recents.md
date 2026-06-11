# JetBrains Kilo VFS Recent Files Plan

## Goal
Ensure every distinct Kilo VFS editor tab opened by the JetBrains plugin appears as a distinct item in IntelliJ Recent Files, while reopening the exact same Kilo VFS file still reuses the existing editor tab.

## Findings
- The PR adds the Kilo VFS/editor stack in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/vfs/` and opens embedded attachments through `SessionUi.openAttachment()`.
- `KiloVirtualFile` already opts into `EditorHistoryManager.IncludeInEditorHistoryFile` and `KiloVfsManager.open()` uses `FileEditorManager.openFile(...)`, so the integration point is correct.
- IntelliJ recent-file source in `$INTELLIJ_REPO` shows Recent Files is built from `EditorHistoryManager.fileList` plus `FileEditorManager.openFiles`, then deduplicated with `HashSet`/`subtract`. Distinct Kilo files therefore need distinct `VirtualFile.equals/hashCode` identity.
- `KiloVirtualFile.equals/hashCode` uses `project + KiloPath`; `KiloPath` includes `params`. Any Kilo editor kind that builds identical params for different user-visible files will collapse into one recent-file entry.
- `AttachmentEditorKind.attachmentParams(...)` currently includes `sessionId`, `messageId`, `partId`, `filename`, `mime`, and `directory`, but not the attachment URL/content identity. Duplicate or blank `partId` values can make different embedded attachments share the same Kilo path.

## Implementation
1. Add a failing regression test before changing behavior.
2. In `AttachmentEditorKindTest`, create two embedded `FileAttachment` values with the same `id`, same filename, same mime, same session/message/directory, and different `data:` URLs. Assert `attachmentParams(...)` differs and that opening both params creates two open `KiloVirtualFile` tabs and two `EditorHistoryManager.fileList` entries.
3. In `KiloVfsManagerTest`, add or strengthen a generic expectation that two distinct Kilo VFS paths remain two distinct open files and two distinct editor-history entries. This documents the Recent Files contract for all Kilo editor kinds.
4. Update `attachmentParams(...)` to add a compact stable identity field, such as `attachmentKey`, derived from the attachment fields that distinguish real files. Use a hash rather than storing the full `data:` URL in the VFS path.
5. Keep the exact same params for the exact same attachment deterministic, so reopening the same attachment still focuses/reuses the same editor instead of duplicating tabs.
6. Update `KiloAttachmentEditorService.fetch(...)` to use the new identity field when resolving file parts, so duplicate or blank `partId` values do not load the wrong attachment content.
7. Update attachment presentation/validity tests for the new param key. Keep existing required params unless the new key is required by the production open path.
8. Add a patch changeset for `kilo-jetbrains`, because this is a user-visible JetBrains plugin bug fix.

## Verification
- Run `./gradlew test --tests ai.kilocode.client.vfs.KiloVfsManagerTest --tests ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest` from `packages/kilo-jetbrains/`.
- Run `./gradlew typecheck` from `packages/kilo-jetbrains/`.
- If the targeted Gradle test selector is not accepted by this build, run the nearest frontend test task or `./gradlew test` from `packages/kilo-jetbrains/`.

## Notes
- No IntelliJ source changes are needed.
- `EditorHistoryManager.IncludeInEditorHistoryFile` is marked internal/experimental in IntelliJ source, but the PR already uses it; this plan does not add a new IntelliJ dependency beyond validating current behavior.
