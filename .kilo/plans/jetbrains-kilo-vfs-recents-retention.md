# JetBrains Kilo VFS Recent Files Retention Plan

## Goal

Fix Kilo JetBrains attachment editor tabs so every distinct attachment remains visible as a distinct Recent Files entry after its editor tab is closed, while reopening the exact same attachment still reuses the existing tab.

## Current Findings

- `Recent Files` is not only a display problem. IntelliJ builds the switcher/recent-file surfaces from `EditorHistoryManager.fileList` plus currently open editor files, then deduplicates by `VirtualFile.equals/hashCode`.
- `RecentFilesSEContributor` additionally filters out currently-open files and only includes closed history files when `vf.isValid()` is true.
- The previous `attachmentKey` fix addresses one identity bug: duplicate or blank `partId` values now produce distinct `KiloPath` params. It does not cover post-close retention.
- Existing tests assert history immediately after open, but no test asserts that Kilo VFS files remain in `EditorHistoryManager.fileList` after closing their editor tabs.
- `KiloVirtualFileSystem.findOrCreateFile(...)` currently creates a fresh `KiloVirtualFile` for each lookup. IntelliJ's identity virtual-file pointer path is URL-keyed and captures a file instance, so transient instances are the likely cause of unreliable post-close recents behavior.

## Approach

Introduce a stable per-project Kilo VFS file cache keyed by canonical `KiloPath`, then add regression coverage for post-close history retention.

## Implementation Steps

1. Add a failing generic post-close regression test in `KiloVfsManagerTest`.
   - Open two distinct `KiloVfsTestKind` files.
   - Flush EDT events.
   - Close both via `KiloVfsManager.close(...)` or `FileEditorManager.closeFile(...)`.
   - Assert there are no open Kilo test files.
   - Assert `EditorHistoryManager.getInstance(project).fileList.filterIsInstance<KiloVirtualFile>()` still contains both canonical paths and both files are valid.

2. Add an attachment-specific post-close regression test in `AttachmentEditorKindTest`.
   - Build two attachments with same `partId`/filename/mime and different `data:` URLs, using `attachmentParams(...)`.
   - Open both and flush.
   - Close both and flush.
   - Assert no attachment Kilo files are open.
   - Assert history still contains two attachment entries with different `attachmentKey` values.
   - This test covers the exact user symptom: distinct attachments should still appear in recents after closing tabs.

3. Add VFS instance-stability coverage in `KiloVirtualFileSystemTest`.
   - Call `findFileByPath(...)` twice for the same serialized path and assert `assertSame(...)`.
   - Call with equivalent params in different insertion order and assert the same cached file is returned.
   - Call with distinct params and assert a different file is returned.

4. Implement a per-project Kilo VFS file cache.
   - Prefer a small project-level light service, e.g. `@Service(Service.Level.PROJECT) class KiloVfsFileCache(private val project: Project)`.
   - Store `ConcurrentHashMap<KiloPath, KiloVirtualFile>` keyed by `path.canonical()`.
   - Expose a method that returns an existing valid cached file for the canonical path or creates/stores a new `KiloVirtualFile(project, canonical)`.
   - Keep the cache project-scoped so project disposal cleans up references and avoids leaking `Project` from an application-level VFS singleton.

5. Route all VFS file creation through the cache.
   - In `KiloVirtualFileSystem.findOrCreateFile(project, path)`, canonicalize the path and verify the kind is registered as today.
   - Return `project.service<KiloVfsFileCache>().findOrCreate(canonical)` instead of constructing `KiloVirtualFile` directly.
   - Preserve `getPath(...)` and `decode(...)` canonicalization from the stable-key work.

6. Keep identity semantics unchanged unless tests prove otherwise.
   - Leave `KiloVirtualFile.equals/hashCode` as `project + path`, so existing open-file dedup and tab reuse behavior remains stable.
   - Do not enable editor history persistence to disk; keep `isPersistedInEditorHistory() = false` as required.

7. Verify the fix.
   - From `packages/kilo-jetbrains/`, run targeted frontend tests:
     - `./gradlew :frontend:test --tests ai.kilocode.client.vfs.KiloVfsManagerTest --tests ai.kilocode.client.vfs.KiloVirtualFileSystemTest --tests ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest`
   - Run `./gradlew typecheck` from `packages/kilo-jetbrains/`.
   - If the Gradle selector applies unexpectedly to backend tasks, rerun the same filters against `:frontend:test` only.

## Expected Result

- Opening the same attachment twice still reuses one tab/history entry.
- Opening distinct attachments, including duplicate `partId` attachments, creates distinct tabs/history entries.
- Closing attachment tabs no longer removes them from in-memory Recent Files during the IDE session.
- Recent-file history remains non-persistent across IDE restarts, preserving the existing product decision.

## Notes

- This fix should stay entirely under `packages/kilo-jetbrains/`; no shared upstream opencode files are involved.
- The IntelliJ APIs involved (`EditorHistoryManager.IncludeInEditorHistoryFile`) are already in use and marked internal/experimental upstream; this plan does not introduce a new dependency category.
