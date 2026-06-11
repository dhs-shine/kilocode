# Fix JetBrains Kilo VFS Image-Like Backend Open

## Problem
Opening a Kilo virtual attachment whose displayed filename ends in an image extension, such as `.png`, must go through the backend/split open path so it appears in the IDE's backend-owned recent/editor history behavior. However, backend opening currently lets IntelliJ image infrastructure classify the contentless Kilo virtual file as an image. `ImageFileService` calls `IfsUtil.getImageProvider(file)`, which calls `file.contentsToByteArray()`. `KiloVirtualFile` intentionally throws from content accessors because these are contentless UI tabs, so the backend logs `UnsupportedOperationException`.

## Updated Learning
- The previous implementation that moved `KiloVfsManager.open(...)` to frontend-local `FileEditorManager.openFile(...)` was wrong for product behavior: Kilo virtual attachments need to be opened from the backend path so they participate in the expected recents/history behavior.
- The backend virtual-open RPC path is intentional and should remain:
  - `KiloVfsManager.open(...)` should call `KiloWorkspaceService.openVirtualPath(...)` asynchronously.
  - `KiloWorkspaceService.openVirtualPath(...)` should call `KiloWorkspaceRpcApi.openVirtualFile(...)`.
  - `KiloWorkspaceRpcApiImpl.openVirtualFile(...)` should decode the Kilo virtual path, resolve the backend project, create the backend-side `KiloVirtualFile`, and navigate it with `OpenFileDescriptor`.
- The image crash root cause is still file-type assignment, not backend opening itself:
  - `KiloVirtualFile` extends `LightVirtualFileBase("", null, 0)` in the old/broken state.
  - `LightVirtualFileBase` already implements `VirtualFileWithAssignedFileType`; passing `null` leaves the assigned file type unset.
  - IntelliJ `FileTypeRegistry.isFileOfType(file, ImageFileType.INSTANCE)` checks assigned file type first, then falls back to filename/extension detection when assigned type is `null`.
  - A displayed filename like `screen.png` is enough to trigger image classification even though `KiloVirtualFile.getFileType()` returns `FileTypes.UNKNOWN`.
- The correct focused fix is to pass `FileTypes.UNKNOWN` into the `LightVirtualFileBase` constructor while keeping content accessors throwing.

## Implementation Plan
1. Revert the latest frontend-local opening changes, but do not lose the file-type fix:
   - Restore `KiloVfsManager` to accept the service `CoroutineScope` constructor dependency.
   - Restore `KiloVfsManager.open(kind, params)` / `open(path)` so they launch a coroutine and call `service<KiloWorkspaceService>().openVirtualPath(path)`.
   - Keep `openLocal(...)` only as the local/test helper path that already existed before the incorrect frontend-local `open(...)` change.
2. Restore the backend virtual-open RPC contract:
   - Re-add `KiloWorkspaceService.openVirtualPath(path)` with the previous try/catch logging wrapper around `call { openVirtualFile(path) }`.
   - Re-add `KiloWorkspaceRpcApi.openVirtualFile(path)`.
   - Re-add `KiloWorkspaceRpcApiImpl.openVirtualFile(path)` and the `project(path: KiloPath)` helper that resolves by project hash, `directory` param, then first open project.
   - Re-add the needed `KiloPath` and `KiloVirtualFileSystem` imports in the backend implementation.
   - Re-add fake RPC tracking for `virtualOpened` and the fake `openVirtualFile(...)` implementation.
3. Keep the actual image-classification fix:
   - Ensure `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/client/vfs/KiloVirtualFile.kt` uses `LightVirtualFileBase("", FileTypes.UNKNOWN, 0)`.
   - Do not change `contentsToByteArray()`, `getInputStream()`, or `getOutputStream()` to return dummy content.
   - Keep `VirtualFileWithoutContent`.
4. Update tests back to backend-open semantics:
   - Restore the `KiloVfsManagerTest` setup that replaces `KiloWorkspaceService` with `FakeWorkspaceRpcApi`.
   - Restore the `open(...)` test so it asserts a canonical generic virtual path is sent to `rpc.virtualOpened`, not that a frontend editor tab opens directly.
   - Keep `openLocal(...)` tests for direct frontend editor behavior under their local helper path.
   - Restore the `KiloWorkspaceServiceTest` case asserting `openVirtualPath("virtual-path")` calls backend RPC directly.
5. Keep/add regression tests for the actual image bug:
   - Keep the existing `KiloFileEditorProvider` `HIDE_OTHER_EDITORS` image-like-name test because it still guards frontend provider selection.
   - Keep or add a `KiloVirtualFileSystemTest`/VFS regression for an image-like filename such as `screen.png` asserting `FileTypeRegistry.getInstance().getFileTypeByFile(file)` is `FileTypes.UNKNOWN` and `FileTypeRegistry.getInstance().isFileOfType(file, FileTypes.UNKNOWN)` is true.
   - If practical, add the same assertion in a backend-side VFS/RPC-adjacent test to document that backend-created Kilo virtual files also block image classification.
6. Preserve unrelated dirty worktree changes:
   - Do not use `git checkout`, `git reset`, or broad reverts.
   - Apply a targeted patch that only undoes the incorrect frontend-local/RPC-removal edits and retains the `FileTypes.UNKNOWN` constructor assignment.

## Verification
1. From `packages/kilo-jetbrains/`, confirm Java 21 with `java -version`.
2. Run targeted tests: `./gradlew :frontend:test --tests "ai.kilocode.client.vfs.KiloVfsManagerTest" --tests "ai.kilocode.client.vfs.KiloFileEditorProviderTest" --tests "ai.kilocode.client.vfs.KiloVirtualFileSystemTest" --tests "ai.kilocode.client.app.KiloWorkspaceServiceTest"`.
3. If a backend VFS regression test is added, run it explicitly, for example `./gradlew :backend:test --tests "ai.kilocode.backend.rpc.KiloVirtualFileSystemBackendTest"`.
4. Run package typecheck: `./gradlew typecheck`.

## Expected Outcome
Kilo virtual attachments are opened through the backend path again, preserving recents/history behavior. Image-like display names no longer classify contentless Kilo virtual files as image files, so backend image infrastructure does not call unsupported content accessors. The Kilo editor provider still hides competing frontend editors for Kilo virtual files.
