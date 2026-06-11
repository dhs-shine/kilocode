# Fix JetBrains Kilo VFS Empty Split-Mode Editor

## Problem
The latest split-mode fix made image-like Kilo virtual attachments open without the remote unsupported-file toast, but the opened tab is empty. The empty view is the backend placeholder editor from `KiloBackendFileEditorProvider` (`JPanel()`), not the frontend `KiloFileEditor` content.

After the first handoff implementation, manual testing still shows two tabs when clicking an attachment: one raw/encoded `kilo` backend wrapper tab with empty content, and one real `Kilo / ...` frontend tab with the image. If both are closed and the file is reopened from Recent Files, the empty backend wrapper can be reopened again.

## Root Cause
- Backend-driven `OpenFileDescriptor(project, file).navigate(true)` needs a backend-side editor provider so the remote bridge considers `kilo` virtual files supported.
- The backend support provider currently creates an actual empty `FileEditor`.
- In split mode, that backend editor becomes the visible editor tab, so the frontend provider is not the final renderer for this open.
- `EditorHistoryManager` only records useful entries from opened editor composites or fallback editor/provider pairs, so a backend open can satisfy history but must be followed by a deliberate frontend handoff.
- `VirtualFile.getUrl()` is constructed as `protocol + "://" + path`. Our `KiloVirtualFileSystem.decode(...)` currently accepts only raw JSON paths, while real editor history/restoration and split-mode wrappers can surface `kilo://{...}` URL-shaped values. The current cleanup test only covered raw JSON wrapper paths, so real wrappers were not always identified.
- Closing the backend wrapper tab is not enough. If its history entry remains, Recent Files can reopen the backend placeholder without going through `KiloVfsManager.open(...)`, so no handoff runs.

## Implementation Plan
1. Keep the backend VFS and backend support provider, but treat it as temporary backend support/history plumbing only.
   - Keep it remote-dev-host gated.
   - Optionally change its component from a blank `JPanel` to a lightweight loading label so any transient state is diagnosable, but do not rely on it for final UI.
   - Do not add content bytes to `KiloVirtualFile`.

2. Change frontend `KiloVfsManager.open(path)` to perform a backend-to-frontend handoff.
   - Launch the existing coroutine.
   - Call `service<KiloWorkspaceService>().openVirtualPath(path)`.
   - If the backend call returns `true`, switch to EDT/Main and open the real frontend `KiloVirtualFile` with `openLocal(...)`.
   - This restores the real `AttachmentEditorKind.createContent(...)` image/text/binary UI while preserving the backend navigation step that avoids unsupported-file rejection and records backend history.

3. Normalize Kilo virtual paths before matching wrappers or accepting files.
    - Extend the shared path decode path to accept both raw JSON VFS paths and URL-shaped `kilo://{json}` values.
    - Do not change `KiloVirtualFileSystem.getPath(...)`; keep canonical raw JSON as the VFS path so existing `KiloVirtualFile` equality and history behavior remain stable.
    - Use the normalized decoder in `KiloVirtualFileSystem.findFileByPath(...)`, `KiloFileEditorProvider.path(...)`, and `KiloVfsManager` wrapper matching.
    - For wrapper matching, inspect both `file.path` and `file.url` when needed. Real split-mode wrappers may expose the `kilo` identity through the URL shape even if the wrapper object is not the exact frontend `KiloVirtualFile` instance.

4. Avoid duplicate empty tabs and stale Recent Files entries after the handoff.
    - After `openLocal(...)`, find backend wrapper files and history entries for the same canonical decoded `KiloPath`.
    - Identify wrappers by decodable canonical Kilo path and `file !is KiloVirtualFile`, not by raw string order.
    - Do not close the frontend `KiloVirtualFile` opened by `openLocal(...)`.
    - Prefer opening the frontend editor first, then closing only non-`KiloVirtualFile` wrappers with the same canonical path to avoid losing focus if the close happens before local open.
    - Remove matching non-`KiloVirtualFile` wrapper entries from `EditorHistoryManager` after closing them, while leaving the frontend `KiloVirtualFile` history entry in place. This ensures Recent Files reopens the real frontend editor, not the backend placeholder.

5. Add or adjust tests.
    - Update `KiloVfsManagerTest.testOpenUsesBackendVirtualFileRpc` so it expects the backend RPC call and then a frontend `KiloFileEditor` tab after the coroutine settles.
    - Add a focused test for handoff cleanup with a URL-shaped wrapper path (`kilo://{json}`): seed/open a decodable non-`KiloVirtualFile` wrapper, run the handoff cleanup, assert the wrapper is closed and removed from `EditorHistoryManager`, and assert the real `KiloVirtualFile`/`KiloFileEditor` remains.
    - Add a recents regression: after handoff cleanup, close the real frontend tab, reopen the remaining recent Kilo entry, and assert the selected editor is `KiloFileEditor` with real content rather than the backend placeholder.
    - Add shared VFS decode coverage for both raw JSON and `kilo://{json}` inputs.
    - Add provider coverage that a decodable URL-shaped `kilo` wrapper is accepted and creates real content.
    - Keep existing provider regression tests for decodable wrapped files and `HIDE_OTHER_EDITORS`.
    - Keep backend VFS decode/image classification tests.

6. Re-check attachment rendering.
    - Verify that after `open(...)`, selected editor is `KiloFileEditor` and its component is not the backend placeholder.
    - Verify only one visible tab remains for the attachment after the backend handoff settles.
    - Verify Recent Files reopens the `KiloVirtualFile` frontend editor, not the backend wrapper.
    - For test kind, assert the component text is still produced (`content:<id>`).
    - For attachment kind, existing tests should continue covering text/image/binary data loading paths if available; add a minimal UI assertion only if current tests do not exercise `AttachmentEditorKind.createContent(...)` after open.

## Verification
Run from `packages/kilo-jetbrains/`:

1. `./gradlew :frontend:test --tests "ai.kilocode.client.vfs.KiloVfsManagerTest" --tests "ai.kilocode.client.vfs.KiloFileEditorProviderTest" --tests "ai.kilocode.client.vfs.KiloVirtualFileSystemTest" --tests "ai.kilocode.client.app.KiloWorkspaceServiceTest"`
2. `./gradlew :backend:test --tests "ai.kilocode.backend.rpc.KiloVirtualFileSystemBackendTest"`
3. `./gradlew typecheck`

## Expected Outcome
Opening an image-like Kilo attachment in split mode should briefly pass through backend-supported navigation, then display the real frontend Kilo attachment editor with the image/content loaded. The unsupported-file toast should remain fixed, and the user should not be left on an empty backend placeholder tab.

---

# Follow-up: Recent Files reopen still shows an empty / JSON-named editor (split mode)

## New Symptom (reported)
1. Clicking an attachment in the session opens the real editor correctly.
2. The file then appears in Recent Files.
3. Reopening it from Recent Files opens an **additional** tab with **empty content** and the **raw JSON path as the tab name** — not the real `KiloFileEditor`.
4. Closing everything and reopening from Recent Files again yields an empty-content editor.

So the deliberate `KiloVfsManager.open()` handoff works, but the Recent Files / Switcher reopen path does not, because it never runs our handoff or cleanup.

## How Recent Files actually works here (from IntelliJ source)
Verified against `$INTELLIJ_REPO` (`platform/recentFiles/**`, `platform/platform-impl/.../EditorHistoryManager.kt`):

- The Switcher reopens an entry by calling **frontend** `FileEditorManager.openFile(value.virtualFile)` (`recentFiles/frontend/switcherNavigation.kt:43`). There is a `com.intellij.recentFiles.navigator` EP that can change the open *mode* and a `com.intellij.recentFiles.excluder` EP (`RecentFilesExcluder`) that can hide entries — neither redirects which file is opened.
- The Switcher model is built on the **backend** from `EditorHistoryManager.getInstance(project).fileList` plus the **frontend** editor selection history that the client passes in (`backendRecentFilesCollector.kt:36,102`; `frontendSwitcherItemsCollector.kt:29` → `FileEditorManagerImpl.getSelectionHistoryList()`).
- `EditorHistoryManager.loadState` is a **no-op on JetBrains Client** (`EditorHistoryManager.kt:327`), so in split mode the frontend does not own persisted recents; the backend history + frontend live selection history drive the list.
- A non-`KiloVirtualFile` wrapper is admitted to history when `VirtualFileManager.findFileByUrl(file.url) != null` (`EditorHistoryManager.kt:104-109`); a `kilo://{json}` url resolves via our VFS, so wrappers do get recorded.
- The tab/switcher name comes from `presentableName` (`backendRecentFilesCollector.kt:186`). A raw-JSON name means the reopened file is **not** a `KiloVirtualFile` (whose `getName()` returns the filename) — it is a generic wrapper whose name falls back to its path, or the backend placeholder editor.

## Registration today (two providers, same editor type id)
- Frontend `kilo.jetbrains.frontend.xml`: VFS `kilo` + `KiloFileEditorProvider` (EP id `KiloVfsEditor`, `getEditorTypeId() = "KiloVfsEditor"`).
- Backend `kilo.jetbrains.backend.xml`: VFS `kilo` + `KiloBackendFileEditorProvider` (EP id `KiloVfsEditorBackend`, **but `getEditorTypeId()` is also `"KiloVfsEditor"`**). The backend editor is an empty `JPanel` (`KiloBackendFileEditorProvider.kt:43`), only active when `AppMode.isRemoteDevHost()` (or unit test).

## Root-cause hypotheses (need one log capture to disambiguate)
The empty + JSON-named reopen means the file opened from recents is rendered by something other than the frontend `KiloFileEditor`. Two candidates:

- **H1 — backend-backed reopen.** The surviving recents entry is the backend `EditorHistoryManager` entry (the backend `navigate` recorded it and we never clean the backend side). Reopening routes the open to the backend, where `KiloBackendFileEditorProvider` produces the empty `JPanel`, projected to the client. Our frontend cleanup only touches the **frontend** `EditorHistoryManager`, so it cannot remove the backend entry.
- **H2 — frontend wrapper reopen.** The recents entry is a client-side wrapper (from frontend selection history) that our deliberate-open `cleanup()` removed once, but a fresh wrapper is recorded again on the projected open and is never converted because reopen bypasses `KiloVfsManager.open()`. If its url/path is not in our decodable forms, `KiloFileEditorProvider.accept` returns false and a default empty editor is shown.

Both share the same fix shape: **a wrapper open from any source must be converted to the canonical frontend `KiloVirtualFile`, and the polluting entry must be removed on the side that owns it.**

## Confirmed context (from user)
- **Environment: split mode / remote dev.** The backend `KiloBackendFileEditorProvider` (empty `JPanel`) is active, so H1 (backend-backed reopen) is the leading hypothesis. Both processes are involved.
- **Approach: diagnostic logging first**, then implement the precise fix and remove the logging.

---

# EXECUTION PLAN (prescriptive — for a fast model)

## Rules for the implementer
- Work only inside `packages/kilo-jetbrains/`. All target files are Kilo-owned (`ai/kilocode/...`); **do not add `kilocode_change` markers** (these paths are entirely Kilo additions).
- Follow repo style: single-word names (`path`, `file`, `kind`, `cfg`), early returns, no `else`, no empty `catch` (use `log.error("...", err)` if you must catch). Prefer `const`/`val`. Do not reformat unrelated lines.
- Java 21 is required for Gradle. Verify with `java -version` first; if missing, run `sdk install java 21-tem && sdk use java 21-tem`.
- All editor/`FileEditorManager`/`EditorHistoryManager` calls run on EDT.
- **Do Phase 1, then STOP and ask the user for logs.** Do not start Phase 2 until the user pastes the `[kilo-vfs]` log lines. The logs select which Phase 2 branch to implement.

---

## PHASE 1 — Diagnostic logging (do this first, then STOP)

Goal: capture, for both the working session-click open and the broken Recent Files reopen, exactly which side and which provider renders the file, and the file's concrete class / `path` / `url` / protocol / decodability. Every line below is prefixed `[kilo-vfs]` so it can be grepped.

### Edit 1 — `frontend/src/main/kotlin/ai/kilocode/client/vfs/KiloFileEditorProvider.kt`
Add imports:
```kotlin
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PlatformUtils
```
In the `companion object`, add a logger and a log line inside `path(...)`:
```kotlin
companion object {
    const val EDITOR_TYPE_ID = "KiloVfsEditor"
    private val LOG = logger<KiloFileEditorProvider>()

    private fun path(file: VirtualFile): KiloPath? {
        if (file is KiloVirtualFile) return file.path
        if (file.fileSystem.protocol != KiloVirtualFileSystem.PROTOCOL && !file.url.startsWith("${KiloVirtualFileSystem.PROTOCOL}://")) return null
        val path = KiloVirtualFileSystem.decode(file.path) ?: KiloVirtualFileSystem.decode(file.url)
        LOG.info("[kilo-vfs] front.path class=${file.javaClass.name} proto=${file.fileSystem.protocol} path=${file.path} url=${file.url} decoded=${path != null} client=${PlatformUtils.isJetBrainsClient()} host=${AppMode.isRemoteDevHost()}")
        if (path?.kind == "attachment") ensureAttachmentEditorKind()
        return path
    }
}
```
In `createEditor(...)`, immediately before `return KiloFileEditor(...)`:
```kotlin
LOG.info("[kilo-vfs] front.createEditor kind=${kilo.path.kind} class=${file.javaClass.name} url=${file.url}")
```

### Edit 2 — `frontend/src/main/kotlin/ai/kilocode/client/vfs/KiloVfsManager.kt`
Add import `import com.intellij.openapi.diagnostic.logger`. Add a class-level field:
```kotlin
private val log = logger<KiloVfsManager>()
```
In `openLocal(path: String, focus: Boolean)`, after `val file = file(...) ?: return false`:
```kotlin
log.info("[kilo-vfs] front.openLocal handoff kind=${parsed.kind} params=${parsed.params}")
```
In `cleanup(...)`, log the counts (compute the matching lists once, log, then act):
```kotlin
val wrappers = manager.openFiles.filter { it.isWrapperFor(path) }
val stale = history.fileList.filter { it.isWrapperFor(path) }
log.info("[kilo-vfs] front.cleanup wrappersClosed=${wrappers.size} historyRemoved=${stale.size} path=${path.kind}/${path.params}")
wrappers.forEach { manager.closeFile(it) }
stale.forEach { history.removeFile(it) }
```

### Edit 3 — `backend/src/main/kotlin/ai/kilocode/backend/vfs/KiloBackendFileEditorProvider.kt`
Add imports:
```kotlin
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PlatformUtils
```
Add to the `companion object` a logger:
```kotlin
private val LOG = logger<KiloBackendFileEditorProvider>()
```
In `createEditor(...)`, before the `return`:
```kotlin
LOG.info("[kilo-vfs] back.createEditor class=${file.javaClass.name} path=${file.path} url=${file.url} host=${AppMode.isRemoteDevHost()} client=${PlatformUtils.isJetBrainsClient()}")
```
In `accept(...)`, just before the final `return KiloVirtualFileSystem.decode(file.path) != null`:
```kotlin
LOG.info("[kilo-vfs] back.accept class=${file.javaClass.name} proto=${file.fileSystem.protocol} path=${file.path} url=${file.url}")
```

### Edit 4 — `backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloWorkspaceRpcApiImpl.kt`
`LOG` already exists. In `openVirtualFile(path)`, after decoding/resolving:
```kotlin
LOG.info("[kilo-vfs] back.openVirtualFile path=$path decoded=${item} project=${project.locationHash}")
```
(place it after `val vf = ...findOrCreateFile(...)`, before `navigate(...)`).

### Build + hand off
- Run `./gradlew typecheck` from `packages/kilo-jetbrains/`. It must pass.
- **STOP. Tell the user:** run the split-mode repro (open an attachment, then reopen it from Recent Files), and paste every `[kilo-vfs]` line from BOTH logs — the JetBrains Client (frontend) log and the host (backend) log. In a Gradle split run these are the two run consoles / their respective `idea.log` sandbox files; grep for `[kilo-vfs]`.

### What the logs decide (Phase 2 branch selector)
- **Branch A — frontend sees the reopen.** On reopen you see `front.path ... decoded=true`. The frontend provider is consulted, so a frontend listener can intercept it. Implement **Step 2A** (listener) + **Step 2C** (type id).
- **Branch B — backend-only reopen.** On reopen you see `back.createEditor` (and/or `back.accept`) but NO `front.path`. The reopen never reaches the frontend, so a listener cannot help. Implement **Step 2B** (backend history exclusion) + **Step 2C** (type id), then re-test; add **Step 2A** as well if a transient wrapper tab still appears.
- **Branch C — decode miss.** On reopen you see `front.path ... decoded=false`. The wrapper url/path shape is one our decoder does not recognize. Copy the exact `url`/`path` from the log into the plan and extend `KiloVirtualFileSystem.raw(...)` (Step 2D) to cover it; then Branch A applies.

---

## PHASE 2 — Fix (implement only the branch(es) the logs select)

### Step 2C — Always: give the backend provider a distinct editor type id
The frontend and backend providers both return `getEditorTypeId() = "KiloVfsEditor"`. History serializes `selectedProvider` by type id, so a frontend entry can resolve to the backend empty editor on restore.

In `backend/.../vfs/KiloBackendFileEditorProvider.kt`, change:
```kotlin
const val EDITOR_TYPE_ID = "KiloVfsEditor"
```
to:
```kotlin
const val EDITOR_TYPE_ID = "KiloVfsEditorBackend"
```
(`getEditorTypeId()` already returns `EDITOR_TYPE_ID`; the frontend keeps `"KiloVfsEditor"`.)

### Step 2A — Frontend listener: adopt any wrapper open into the real editor
New file `frontend/src/main/kotlin/ai/kilocode/client/vfs/KiloVfsReopenListener.kt`:
```kotlin
package ai.kilocode.client.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class KiloVfsReopenListener(private val project: Project) : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file is KiloVirtualFile) return
        val path = KiloVirtualFileSystem.decode(file.path) ?: KiloVirtualFileSystem.decode(file.url) ?: return
        if (service<KiloVfsRegistry>().get(path.kind) == null) return
        ApplicationManager.getApplication().invokeLater({
            project.service<KiloVfsManager>().adopt(file, path)
        }, project.disposed)
    }
}
```
Add `adopt(...)` to `KiloVfsManager` (reuses the proven `open()` handoff, which on success runs `openLocal` + `cleanup`, so it both renders the real editor and closes/removes the wrapper). Guard with a "real already open" check to converge and avoid repeated backend RPC:
```kotlin
@RequiresEdt
fun adopt(wrapper: VirtualFile, path: KiloPath) {
    if (wrapper is KiloVirtualFile) return
    val canonical = path.copy(projectHash = project.locationHash)
    val manager = FileEditorManager.getInstance(project)
    val present = manager.openFiles.any { it is KiloVirtualFile && it.path == canonical }
    if (present) {
        manager.closeFile(wrapper)
        EditorHistoryManager.getInstance(project).removeFile(wrapper)
        return
    }
    open(KiloVirtualFileSystem.getInstance().getPath(canonical))
}
```
Register the listener in `frontend/src/main/resources/kilo.jetbrains.frontend.xml` by adding a top-level `<projectListeners>` block (sibling of `<extensions>` and `<actions>`):
```xml
<projectListeners>
    <listener class="ai.kilocode.client.vfs.KiloVfsReopenListener"
              topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"/>
</projectListeners>
```
Convergence/reentry reasoning to verify in tests: `open()` → `openLocal` opens the canonical `KiloVirtualFile` → `fileOpened` fires for a `KiloVirtualFile` → listener returns immediately (`file is KiloVirtualFile`). Any re-projected wrapper hits the `present == true` branch and is just closed. No infinite loop.

### Step 2B — Backend: keep Kilo files out of the host's editor history (only for Branch B)
In `shared/src/main/kotlin/ai/kilocode/client/vfs/KiloVirtualFile.kt`, add import `import com.intellij.idea.AppMode` and override so the file is recorded in client (frontend) recents but NOT in the backend host history (which is what drives the reopenable empty entry):
```kotlin
override fun isIncludedInEditorHistory(project: Project): Boolean = !AppMode.isRemoteDevHost()
```
(`KiloVirtualFile` already implements `EditorHistoryManager.IncludeInEditorHistoryFile`; this overrides its default `true`. `isPersistedInEditorHistory()` stays `false`.) Keep the backend support provider as-is — it remains the transient openability shim that prevents the unsupported-file toast; it must never be a durable recents target.

### Step 2D — Extend the decoder (only for Branch C)
In `shared/.../vfs/KiloVirtualFileSystem.kt`, the private `raw(path)` currently accepts raw JSON, `kilo://{json}`, and `kilo://%7B...`. If the logs show a different wrapper shape (e.g. an rd-specific prefix wrapping the `kilo://` url), extend `raw(...)` to strip/recognize that exact shape and return the inner JSON. Add the observed example to a `KiloVirtualFileSystemTest` decode case. Do not broaden so far that unrelated non-JSON paths start decoding.

---

## PHASE 3 — Tests (extend existing suites; no mocks of EDT/threading)

All in `frontend/src/test/kotlin/ai/kilocode/client/vfs/`. Use the existing `KiloVfsManagerTest` helpers (`edt {}`, `waitFor {}`, `RemoteKiloFile`, `FakeWorkspaceRpcApi`).

1. **Adopt converts a reopened wrapper into the real editor (primary, Step 2A).** Open a decodable `RemoteKiloFile` wrapper directly via `FileEditorManager.openFile` (simulating a recents/projection reopen with no `KiloVfsManager.open`), then call `project.service<KiloVfsManager>().adopt(wrapper, canonical)` on EDT. `waitFor` until `rpc.virtualOpened` is non-empty, a `KiloVirtualFile` with the expected params is open, and the wrapper is gone from both `openFiles` and `EditorHistoryManager.fileList`. Assert selected editor is `KiloFileEditor` with `content:<id>` and exactly one `KiloVirtualFile` open.
2. **Reentry guard.** `adopt(realKiloVirtualFile, path)` is a no-op: no extra RPC, no close. Open the real file first, snapshot `rpc.virtualOpened.size`, call `adopt` with it, assert size unchanged and the editor still open.
3. **Present-guard (no duplicate RPC).** With the canonical `KiloVirtualFile` already open, calling `adopt(wrapper, canonical)` closes the wrapper and removes its history entry without adding to `rpc.virtualOpened`.
4. **Optional listener wiring.** Publish to the topic in-process and assert the same outcome as test 1:
   `project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(manager, wrapper)` then drain EDT via `UIUtil.dispatchAllInvocationEvents()`.
5. **Type id (Step 2C).** Assert `KiloBackendFileEditorProvider().editorTypeId == "KiloVfsEditorBackend"` (add/keep this in the backend test module if a backend unit test exists; otherwise assert the frontend stays `"KiloVfsEditor"` in `KiloFileEditorProviderTest`).
6. Keep all existing `KiloVfsManagerTest` / `KiloFileEditorProviderTest` / `KiloVirtualFileSystemTest` / `AttachmentEditorKindTest` cases green.

---

## PHASE 4 — Cleanup
- Remove every Phase 1 `[kilo-vfs]` `log.info` line and any now-unused imports (`logger`, `PlatformUtils`, `AppMode`) that were added only for logging. Keep imports/loggers only where Phase 2 still uses them.
- Re-run typecheck.

---

## Verification (run from `packages/kilo-jetbrains/`)
1. `./gradlew :frontend:test --tests "ai.kilocode.client.vfs.*" --tests "ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest"`
2. `./gradlew :backend:test --tests "ai.kilocode.backend.rpc.KiloVirtualFileSystemBackendTest"`
3. `./gradlew typecheck`
4. Manual split mode: open attachment → exactly one real `KiloFileEditor`; reopen from Recent Files → one real `KiloFileEditor`, no empty/JSON-named duplicate; close all and reopen again → real content.

## Expected outcome
Reopening a Kilo attachment from Recent Files in split mode always lands on the real frontend `KiloFileEditor` with content, with no empty/JSON-named duplicate tab and no duplicate recents entry, while the unsupported-file toast stays fixed.

## Resolved decisions
1. **Repro environment:** split mode / remote dev (backend provider active). — confirmed.
2. **Diagnostic logging first:** land Phase 1 logging, capture one recents reopen, then implement the targeted Phase 2 branch and remove the logging. — confirmed.

## Files touched (summary)
- Phase 1 (temporary): `frontend/.../vfs/KiloFileEditorProvider.kt`, `frontend/.../vfs/KiloVfsManager.kt`, `backend/.../vfs/KiloBackendFileEditorProvider.kt`, `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt`.
- Phase 2: `backend/.../vfs/KiloBackendFileEditorProvider.kt` (type id), `frontend/.../vfs/KiloVfsReopenListener.kt` (new), `frontend/.../vfs/KiloVfsManager.kt` (`adopt`), `frontend/src/main/resources/kilo.jetbrains.frontend.xml` (listener); conditionally `shared/.../vfs/KiloVirtualFile.kt` (Branch B) and `shared/.../vfs/KiloVirtualFileSystem.kt` (Branch C).
- Phase 3: `frontend/src/test/kotlin/ai/kilocode/client/vfs/KiloVfsManagerTest.kt` (+ provider/decoder tests as needed).
