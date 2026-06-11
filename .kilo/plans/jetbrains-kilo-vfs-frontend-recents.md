# JetBrains Kilo VFS Frontend Recent Files Plan

## Goal

Make Kilo JetBrains attachment editor files show as distinct Recent Files entries while open and remain in Recent Files after their tabs are closed, including in the current split/native Recent Files implementation.

## Findings

- The previous stable-key/cache fix is still useful, but it only proves `EditorHistoryManager.fileList` can retain stable `KiloVirtualFile` instances.
- The user-visible `RecentFiles` action is the new frontend Recent Files implementation, not only the older `EditorHistoryManager`/fallback switcher path.
- IntelliJ's new Recent Files flow renders `FrontendRecentFilesModel`, populated by `RecentlySelectedEditorListener` plus backend metadata/history updates.
- Kilo attachment files are frontend-created non-physical virtual files. In split/native Recent Files, backend history is not a reliable source for frontend-only Kilo files after close.
- `RECENTLY_OPENED_UNPINNED` intentionally removes files after close. When it becomes empty or size one, the frontend model falls back to `RECENTLY_OPENED`; Kilo closed attachments must therefore be explicitly present in `RECENTLY_OPENED`.
- The platform exposes `FrontendRecentFilesModel.applyFrontendChanges(...)` and `RecentFileKind`/`FileChangeKind` as internal APIs. We already rely on internal/experimental editor-history APIs, so using this narrowly for Kilo VFS files is acceptable if isolated and tested.
- Two attachments with the same filename can still look identical because `AttachmentEditorKind.title(...)` and `presentablePath(...)` currently use only `sessionId` and filename. Distinct model entries should be asserted by `KiloPath`, and display disambiguation should be considered if tests show visually identical rows.

## Approach

Add a Kilo-owned frontend recents bridge for `KiloVirtualFile` instances. It will keep a small per-project MRU list of valid Kilo VFS files and explicitly feed those files into `FrontendRecentFilesModel.RECENTLY_OPENED` on open and after close. This supplements platform history without changing editor-history persistence or moving Kilo VFS files to backend/shared modules.

## Implementation Steps

1. Add frontend Recent Files regression coverage.
   - In `KiloVfsManagerTest`, open two distinct `KiloVfsTestKind` files and wait for `FrontendRecentFilesModel.getInstance(project).getRecentFiles(RecentFileKind.RECENTLY_OPENED)` to contain both corresponding `KiloVirtualFile`s.
   - Close both files and assert `FileEditorManager.openFiles` has none of them while `RECENTLY_OPENED` still contains both.
   - Keep the existing `EditorHistoryManager.fileList` assertions as fallback-switcher coverage.

2. Add attachment-specific frontend Recent Files coverage.
   - In `AttachmentEditorKindTest`, open two embedded attachments with the same `partId`/filename and different URLs, using `attachmentParams(...)` so `attachmentKey` differs.
   - Assert `RECENTLY_OPENED` contains two attachment `KiloVirtualFile`s with both `attachmentKey` values while open.
   - Close both tabs and assert `RECENTLY_OPENED` still contains the same two attachment keys.
   - Assert `RECENTLY_OPENED_UNPINNED` does not need to retain closed files; the important closed-file surface is `RECENTLY_OPENED`.

3. Add a small Kilo-owned bridge service.
   - Create a project-level light service, for example `KiloVfsRecentFiles` under `ai.kilocode.client.vfs`.
   - Store recent Kilo files by canonical `KiloPath`, newest first, using the cached `KiloVirtualFile` instances.
   - Filter invalid files and cap the list to `UISettings.getInstance().recentFilesLimit + 1` or a small safe bound if `UISettings` is awkward in tests.
   - Isolate all imports of `com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel`, `RecentFileKind`, and `FileChangeKind` in this service.

4. Feed the frontend Recent Files model from `KiloVfsManager`.
   - After `FileEditorManager.openFile(file, focus)`, call the bridge to record the file and apply it to `RECENTLY_OPENED` and `RECENTLY_OPENED_UNPINNED` with `FileChangeKind.ADDED`.
   - After `FileEditorManager.closeFile(file)`, call the bridge to re-apply the cached Kilo MRU list to `RECENTLY_OPENED` only, so closed Kilo files remain recent but do not pollute the unpinned/open-editor switcher state.
   - Keep `isPersistedInEditorHistory() = false` unchanged.

5. Preserve distinct identity and optional display disambiguation.
   - Keep identity based on canonical `KiloPath` including `attachmentKey`.
   - If the frontend model tests show two entries exist but are visually indistinguishable, update `AttachmentEditorKind.presentablePath(...)` to include a stable discriminator such as `messageId` and a short `attachmentKey` suffix while keeping the tab title as the filename.
   - Add a presentation test only if this display change is needed.

6. Verify.
   - From `packages/kilo-jetbrains/`, run:
     - `./gradlew :frontend:test --tests ai.kilocode.client.vfs.KiloVfsManagerTest --tests ai.kilocode.client.vfs.KiloVirtualFileSystemTest --tests ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest`
     - `./gradlew typecheck`

## Expected Result

- Two distinct attachments appear as two Recent Files model entries while open.
- Closing all attachment tabs leaves both attachments in `RECENTLY_OPENED` during the current IDE session.
- Closed attachments are not persisted across IDE restarts.
- Reopening the exact same attachment still reuses the same editor tab because `KiloPath`/`KiloVirtualFile` identity remains unchanged.

## Caveats

- This uses IntelliJ's internal frontend Recent Files model API in a narrow Kilo VFS boundary. The alternative would be a broader split-mode redesign that makes Kilo VFS files backend-originated, which is much larger and unnecessary for this bug.
- Search Everywhere's `RecentFilesSEContributor` is a different surface and maps files through `PsiManager.findFile(...)`; this plan targets the `RecentFiles` action described by the user.
