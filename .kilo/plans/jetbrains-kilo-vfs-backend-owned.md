# JetBrains Backend-Owned Kilo VFS Plan

## Goal

Make Kilo virtual editor files first-class backend-owned IntelliJ `VirtualFile`s so editor tabs, `VirtualFileId`, editor history, and Recent Files all see the same canonical file identity.

The frontend must not ask the backend to open an `attachment` or any other specific Kilo editor kind. It should pass one generic Kilo virtual-file path string. The backend should resolve/open that path without per-kind methods or per-kind dispatch.

## Current State

- Kilo VFS is currently registered only in `frontend/src/main/resources/kilo.jetbrains.frontend.xml`.
- `KiloVfsManager.open(kind, params)` creates a frontend `KiloVirtualFile` and calls frontend `FileEditorManager.openFile(...)`.
- This works for rendering tabs but leaves backend history/Recent Files/RPC identity unreliable in split mode.
- The current uncommitted cache change adds `KiloVfsFileCache` and history-only tests. That was a local workaround for frontend-created files and should be dropped for this backend-owned design.
- Keep the committed stable identity work: canonical params, no `launchId`, and deterministic `attachmentKey` remain necessary.

## Target Architecture

| Concern | Owner |
|---|---|
| Kilo VFS protocol/path decoding | shared code loaded by backend and frontend |
| Canonical `KiloPath` identity | shared |
| Backend `VirtualFile` creation/resolution | backend via shared VFS implementation |
| Opening a Kilo virtual file | backend RPC using a generic path string |
| Editor history / Recent Files identity | backend/platform |
| Swing editor UI and attachment rendering | frontend `FileEditorProvider` / `KiloEditorKind` registry |
| Attachment-specific params | frontend code that builds the path before RPC |

Desired open flow:

```text
frontend click
  -> build canonical Kilo virtual-file path string
  -> RPC openVirtualFile(path)
  -> backend resolves path to backend KiloVirtualFile
  -> backend opens/navigates VirtualFile in platform editor model
  -> frontend editor UI appears via platform split-mode synchronization
  -> Recent Files tracks the backend-originated VirtualFile
```

## Design Details

### Generic Path Contract

- Add a generic RPC method such as `KiloWorkspaceRpcApi.openVirtualFile(path: String): Boolean`.
- The payload is only the virtual-file path string, not `kind`, `params`, or attachment-specific fields.
- Keep existing `openFile(path: String)` for real local files. Do not overload it with Kilo paths because its current normalization strips query/fragment-like text and is local-file-specific.
- The Kilo path format should continue to encode `KiloPath(projectHash, kind, params)` with canonical params.
- Backend should treat `projectHash` as a hint, not as the only project lookup key. In split mode the frontend project hash may not equal the backend project hash.
- Backend project resolution order for a Kilo path:
  1. open project whose `locationHash` matches `KiloPath.projectHash`, if any;
  2. open project whose `basePath` contains or matches `params["directory"]`, when present;
  3. first non-default open project as a final fallback.
- After resolving the backend project, backend should canonicalize the file path using the backend project hash before opening. This makes the backend-created `VirtualFile` the authoritative identity.

### Shared VFS Core

- Move the generic, non-UI VFS pieces into `shared` so both backend and frontend load the same protocol implementation:
  - `KiloPath`
  - canonical param helpers
  - Kilo path encode/decode helpers
  - generic `KiloVirtualFileSystem`
  - generic `KiloVirtualFile`
- Preserve existing package names where practical to avoid a broad package rename.
- Remove dependencies from these generic classes on frontend-only `KiloVfsRegistry` or `KiloEditorKind`.
- Backend `KiloVirtualFile` validity should be generic: valid when the project is not disposed and the path decodes/canonicalizes. It should not know whether `kind == "attachment"` or any other kind exists.
- Generic backend presentation can use stable params only, for example `filename`, `name`, or `kind`. The frontend editor can still use `KiloEditorKind.title(...)` for the actual editor tab name.

### Module Registrations

- Register the same Kilo VFS implementation in backend XML:
  - `packages/kilo-jetbrains/backend/src/main/resources/kilo.jetbrains.backend.xml`
  - `<virtualFileSystem key="kilo" implementationClass="...KiloVirtualFileSystem"/>`
- Keep frontend VFS registration so frontend deserialization/path lookup can resolve backend-originated files.
- Keep `KiloFileEditorProvider` registered only in frontend XML, because it creates Swing UI.
- Keep `plugin.xml` module content unchanged unless module descriptor dependencies need a corresponding update.

### Backend Opening

- Implement backend `openVirtualFile(path)` in `KiloWorkspaceRpcApiImpl`.
- Decode and validate that the path belongs to Kilo VFS.
- Resolve backend project using the generic lookup rules above.
- Resolve/create the backend `KiloVirtualFile` through `KiloVirtualFileSystem`.
- Navigate it using the existing backend `navigate(project, file)` helper based on `OpenFileDescriptor(project, file).navigate(true)`.
- Do not dispatch on `KiloPath.kind`; backend should only parse the generic path structure.
- If split-mode testing shows backend navigation does not consistently open the visible frontend editor, add a second generic fallback method that returns `VirtualFileId` for the backend-originated file and let the frontend open `id.virtualFile()` on EDT. This uses experimental IntelliJ API and should stay isolated behind the Kilo VFS boundary.

### Frontend Opening

- Change `KiloVfsManager` from a frontend-local file opener into a generic RPC bridge.
- It should build/accept a Kilo virtual-file path string and call `KiloWorkspaceRpcApi.openVirtualFile(path)` from a coroutine, not on EDT.
- It may keep a convenience `open(kind, params)` wrapper only if it simply encodes `KiloPath` and delegates to the path-based method. The backend API remains path-only.
- Update `SessionUi.openAttachment(...)` to use this backend-backed generic open path.
- Keep attachment-specific logic limited to `attachmentParams(...)`; the backend receives only the encoded Kilo virtual-file path.

### Frontend Editor Provider

- Keep `KiloEditorKind`, `KiloVfsRegistry`, `KiloFileEditorProvider`, and `KiloFileEditor` in frontend.
- Update `KiloFileEditorProvider.accept(...)` to accept Kilo protocol files by decoded path/kind, not by assuming the file was manually created on the frontend.
- `KiloFileEditorProvider.createEditor(...)` can still require a frontend-resolved `KiloVirtualFile` if the shared frontend VFS creates that class from the backend path.
- `AttachmentEditorKind.createContent(...)` remains frontend-only and loads attachment content using existing session RPC/services.

## Drop Current Uncommitted Work

Before implementing the backend-owned design, remove the cache workaround from the working tree:

- Delete `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/vfs/KiloVfsFileCache.kt`.
- Revert the uncommitted `KiloVirtualFileSystem.findOrCreateFile(...)` change that routes through `KiloVfsFileCache`.
- Remove uncommitted tests that only assert closed frontend-created files remain in `EditorHistoryManager.fileList`:
  - `KiloVfsManagerTest.testClosedDistinctPathsRemainInHistory`
  - `AttachmentEditorKindTest.testClosedDuplicatePartAttachmentsRemainInHistory`
  - `KiloVirtualFileSystemTest.testFindFileByPathReusesCanonicalFileInstance`, unless shared/backend VFS still intentionally caches instances.
- Do not drop committed stable-key or `attachmentKey` changes.

## Implementation Steps

1. Clean the workaround.
   - Remove the uncommitted cache file and cache-only tests listed above.
   - Ensure the working tree only contains changes needed for backend-owned VFS.

2. Extract shared VFS core.
   - Move/copy generic `KiloPath` encoding and canonicalization into `shared`.
   - Move/refactor `KiloVirtualFileSystem` and `KiloVirtualFile` into shared-compatible code with no frontend registry dependency.
   - Keep generic `KiloVirtualFile` path equality stable and based on backend project + canonical path.

3. Rewire frontend VFS UI code.
   - Adjust imports after the shared extraction.
   - Keep `KiloEditorKind` and registry frontend-only.
   - Make the editor provider decode/check Kilo path kind via shared path helpers.
   - Keep the actual editor UI creation unchanged.

4. Register backend VFS.
   - Add the Kilo VFS extension to `kilo.jetbrains.backend.xml`.
   - Add any required backend Gradle IntelliJ module dependency only if compilation requires it.

5. Add generic backend RPC opening.
   - Add `openVirtualFile(path: String): Boolean` to `KiloWorkspaceRpcApi`.
   - Implement it in `KiloWorkspaceRpcApiImpl` using shared Kilo path decode/project resolution/backend VFS lookup/existing `navigate(...)`.
   - Update `FakeWorkspaceRpcApi` for frontend tests.

6. Change frontend attachment opening.
   - Update `KiloVfsManager` to call `openVirtualFile(path)` via durable RPC from a coroutine.
   - Update `SessionUi.openAttachment(...)` to call the backend-backed path opener.
   - Ensure no RPC call runs on EDT.

7. Add tests for the new contract.
   - Shared/frontend path tests: canonical param order and stable attachment `attachmentKey` remain unchanged.
   - Frontend service test: attachment opening sends exactly one generic virtual-file path string to RPC and does not call RPC on EDT.
   - Backend VFS test: decoding the frontend-built path resolves a backend `KiloVirtualFile` with backend project identity and canonical params.
   - Backend open test: `openVirtualFile(path)` opens a Kilo `VirtualFile` through the real editor manager in monolith test mode with a test file editor provider.
   - Recent Files regression: after backend `openVirtualFile(path)` opens two attachment-like paths, `EditorHistoryManager.fileList` contains two distinct Kilo files by canonical path/`attachmentKey`; after closing tabs, history still contains them. This is the backend-owned equivalent of the current failing behavior.

8. Split-mode/manual verification.
   - Run the plugin in split mode and click two embedded attachments with the same filename/part id but different URLs.
   - Verify both tabs open, both appear in Recent Files while open, and both remain in Recent Files after closing tabs.
   - If tabs do not appear from backend `navigate(...)`, implement the generic `VirtualFileId` fallback described above and repeat the same verification.

## Verification Commands

From `packages/kilo-jetbrains/`:

```bash
./gradlew :frontend:test --tests ai.kilocode.client.vfs.KiloVirtualFileSystemTest --tests ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest
./gradlew :backend:test --tests '*Kilo*Virtual*' --tests '*KiloWorkspaceRpcApiImpl*'
./gradlew typecheck
```

Also run the JetBrains DevKit frontend/backend API usage inspection for the touched files, because this change intentionally crosses split-mode boundaries.

## Expected Result

- Backend owns canonical Kilo `VirtualFile` identity.
- Frontend passes only a generic virtual-file path string to backend.
- Backend does not expose attachment-specific or kind-specific open methods.
- Frontend still owns Kilo editor UI rendering.
- Recent Files and editor history track backend-originated Kilo files consistently.
- The cache workaround is gone unless a later test proves frontend deserialization still needs a small identity cache.

## Risks

- `VirtualFileId` fallback, if needed, uses experimental IntelliJ APIs. Keep it isolated and only use it if backend navigation does not open the visible frontend tab reliably.
- Generic backend presentation may be less rich than frontend presentation. That is acceptable initially; identity correctness matters first. Add path/name polish only if Recent Files rows are visually ambiguous after identity is fixed.
- Moving VFS core into shared can expose package awkwardness if package names are preserved. Prefer the smallest import churn now over a broad package rename.
