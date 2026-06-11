# JetBrains Kilo VFS — backend-owned identity, frontend-rendered editor (PRESCRIPTIVE)

Execution plan for a fast model. Supersedes `jetbrains-kilo-vfs-empty-editor-handoff.md`. Implementing this **reverts/deletes** most of the currently-uncommitted VFS code (backend placeholder provider, `adopt`, reopen listener, `openVirtualFile` RPC).

## Rules for the implementer

- Work only inside `packages/kilo-jetbrains/`. All target files are Kilo-owned (`ai/kilocode/...`) — **do NOT add `kilocode_change` markers**.
- Repo style: single-word names (`path`, `file`, `kind`, `dir`, `cfg`), early returns, no `else`, no empty `catch` (log via the existing logger). Prefer `val`. Don't reformat untouched lines.
- Java 21 required for Gradle. Verify `java -version` first; if not 21, `sdk install java 21-tem && sdk use java 21-tem`.
- All `FileEditorManager` / `EditorHistoryManager` calls run on EDT.
- Use `$INTELLIJ_REPO` (`/Users/kirillk/products/intellij-community`) to confirm any API you are unsure of (e.g. the project-closing topic in Step 12).
- Do the steps in order. Build/typecheck after Phase 1, then after Phase 2.

## Why (condensed root cause, verified in `$INTELLIJ_REPO`)

- A custom **Swing** `FileEditor` over a non-physical VFS file can only render **client-side**; content is not transported backend→frontend. The platform pattern (reworked Terminal: `plugins/terminal/frontend/...`) registers the provider **frontend-only**, with **no backend provider/placeholder**, and opens via `FileEditorManager.openFile` (not backend `navigate()`).
- Backend `openFile` delegates to the client manager when the active `ClientId` isn't local (`FileEditorManagerImpl.kt:1015-1021`), so a **frontend** open bypasses the backend `canNavigate` gate that the placeholder was added to satisfy (`FileNavigatorImpl.kt:21-33`).
- **Startup crash** (`Unknown Kilo editor kind: attachment`): the kind is registered from a `postStartupActivity`, but editor restore runs **before** post-startup (`ProjectManagerImpl.kt:784-786` vs `:844-848`) and restore can call `createEditor` **without** `accept` (by saved `editor-type-id`, `EditorCompositeModelManager.kt:102-104`). Fix = register the kind **synchronously inside `accept` and `createEditor`**.
- **Blink + duplicate recents**: every open round-trips through backend `navigate()` → empty `JPanel` placeholder (name = raw JSON) → frontend editor → cleanup, and the backend `navigate` records a duplicate `EditorHistoryManager` entry the frontend cleanup never removes. Remove the backend leg → both gone.
- **Recents/restart**: backend owns persisted recents/open-editors; the client skips restore unless a registry key is set; the backend can't render our editor → split-mode auto-reopen must be **Kilo-driven** (Phase 2).

---

# PHASE 1 — Architecture fix (crash, blink, duplicate recents)

### Step 1 — `frontend/.../vfs/KiloFileEditorProvider.kt` (register kind eagerly; pure `path()`)

Replace the whole file with:

```kotlin
package ai.kilocode.client.vfs

import ai.kilocode.client.session.ui.attachment.ensureAttachmentEditorKind
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class KiloFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        ensureAttachmentEditorKind()
        val path = path(file) ?: return false
        return service<KiloVfsRegistry>().get(path.kind) != null
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        ensureAttachmentEditorKind()
        val path = path(file) ?: error("Invalid Kilo virtual file: ${file.path}")
        val kilo = file as? KiloVirtualFile ?: KiloVirtualFile(project, path.copy(projectHash = project.locationHash))
        val kind = service<KiloVfsRegistry>().get(kilo.path.kind) ?: error("Unknown Kilo editor kind: ${kilo.path.kind}")
        return KiloFileEditor(project, file, kilo, kind)
    }

    override fun disposeEditor(editor: FileEditor) {
        Disposer.dispose(editor)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS

    companion object {
        const val EDITOR_TYPE_ID = "KiloVfsEditor"

        private fun path(file: VirtualFile): KiloPath? {
            if (file is KiloVirtualFile) return file.path
            if (file.fileSystem.protocol != KiloVirtualFileSystem.PROTOCOL && !file.url.startsWith("${KiloVirtualFileSystem.PROTOCOL}://")) return null
            return KiloVirtualFileSystem.decode(file.path) ?: KiloVirtualFileSystem.decode(file.url)
        }
    }
}
```

Changes vs current: `ensureAttachmentEditorKind()` is the first line of both `accept` and `createEditor`; the `if (path?.kind == "attachment") ensureAttachmentEditorKind()` side-effect is removed from `path()`. `KiloVfsRegistry` stays as-is.

### Step 2 — `frontend/.../session/ui/attachment/AttachmentEditorKind.kt` (drop the activity)

- Delete `AttachmentEditorKindActivity` (the `internal class … : ProjectActivity { … }` block, ~lines 169-173).
- Remove the now-unused `import com.intellij.openapi.startup.ProjectActivity`.
- Keep `AttachmentEditorKind` as an `object`; keep `ensureAttachmentEditorKind()` exactly as is (`service<KiloVfsRegistry>().register(AttachmentEditorKind)`). `SessionUi.kt:187` still calls it — leave that call.

### Step 3 — `frontend/.../vfs/KiloVfsManager.kt` (frontend-only open; delete band-aids)

Replace the whole file with (keep `cs` — Phase 2 uses it):

```kotlin
package ai.kilocode.client.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class KiloVfsManager(
    private val project: Project,
    private val cs: CoroutineScope,
) {
    @RequiresEdt
    fun open(kind: String, params: Map<String, String> = emptyMap()) {
        openLocal(kind, params, focus = true)
    }

    @RequiresEdt
    fun openLocal(kind: String, params: Map<String, String> = emptyMap(), focus: Boolean = true): Boolean {
        val file = file(kind, params) ?: return false
        if (ApplicationManager.getApplication().isUnitTestMode) {
            file.putUserData(FileEditorProvider.KEY, KiloFileEditorProvider())
        }
        FileEditorManager.getInstance(project).openFile(file, focus)
        return true
    }

    @RequiresEdt
    fun close(kind: String, params: Map<String, String> = emptyMap()) {
        val file = file(kind, params) ?: return
        FileEditorManager.getInstance(project).closeFile(file)
    }

    @RequiresEdt
    fun updatePresentation(kind: String, params: Map<String, String> = emptyMap()) {
        val file = file(kind, params) ?: return
        FileEditorManager.getInstance(project).updateFilePresentation(file)
    }

    private fun file(kind: String, params: Map<String, String>): KiloVirtualFile? {
        return KiloVirtualFileSystem.getInstance().refreshAndFindFileByPath(path(kind, params)) as? KiloVirtualFile
    }

    private fun path(kind: String, params: Map<String, String>): String {
        return KiloVirtualFileSystem.getInstance().getPath(KiloPath(project.locationHash, kind, params))
    }
}
```

Deleted: `open(path: String)` (RPC), `openLocal(path: String, …)`, `cleanup(...)`, `adopt(...)`, `VirtualFile.isWrapperFor(...)`, and the `KiloWorkspaceService` / `EditorHistoryManager` / `VirtualFile` / coroutine imports they used.

`SessionUi.openAttachment` (line 596) calls `open(kind, params)` from an EDT click handler — no change needed.

### Step 4 — Delete `frontend/.../vfs/KiloVfsReopenListener.kt`

Remove the file. (Phase 2 adds a different, legitimate listener.)

### Step 5 — `frontend/src/main/resources/kilo.jetbrains.frontend.xml`

- Remove the attachment post-startup activity line:
  `<postStartupActivity implementation="ai.kilocode.client.session.ui.attachment.AttachmentEditorKindActivity"/>`
- Remove the entire `<projectListeners>` block that registers `KiloVfsReopenListener`.
  (Both are re-added with new classes in Phase 2 — Step 13.)
- Leave `<virtualFileSystem key="kilo" …>` and `<fileEditorProvider id="KiloVfsEditor" …>` unchanged.

### Step 6 — Delete the backend placeholder

- Delete `backend/src/main/kotlin/ai/kilocode/backend/vfs/KiloBackendFileEditorProvider.kt`.
- In `backend/src/main/resources/kilo.jetbrains.backend.xml`, remove:
  `<fileEditorProvider id="KiloVfsEditorBackend" implementation="ai.kilocode.backend.vfs.KiloBackendFileEditorProvider"/>`
  **Keep** `<virtualFileSystem key="kilo" implementationClass="ai.kilocode.client.vfs.KiloVirtualFileSystem"/>` (lets the backend resolve `kilo://` urls so recents show a proper name; the backend still never renders a kilo editor).

### Step 7 — Remove `openVirtualFile` RPC (frontend → backend navigate)

- `shared/.../rpc/KiloWorkspaceRpcApi.kt`: delete the `openVirtualFile` declaration and its doc comment (~lines 52-53).
- `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt`: delete `override suspend fun openVirtualFile(...)` (lines 190-199) **and** the now-unused private `project(path: KiloPath)` (lines 289-300). Remove the now-unused imports `ai.kilocode.client.vfs.KiloPath` and `ai.kilocode.client.vfs.KiloVirtualFileSystem`.
- `frontend/.../app/KiloWorkspaceService.kt`: delete `openVirtualPath(...)` (lines 139-146).

### Step 8 — `shared/.../vfs/KiloVirtualFile.kt` (history flag)

- Delete the override `override fun isIncludedInEditorHistory(project: Project): Boolean = !AppMode.isRemoteDevHost()` (line 45) so it inherits the `IncludeInEditorHistoryFile` default (`true`).
- Remove `import com.intellij.idea.AppMode`.
- Keep `isPersistedInEditorHistory(): Boolean = false`.

### Phase 1 test edits

- `frontend/.../testing/FakeWorkspaceRpcApi.kt`: delete `override suspend fun openVirtualFile(...)` and the `val virtualOpened` field. (Phase 2 re-adds two methods + fields — Step 14.)
- `frontend/.../app/KiloWorkspaceServiceTest.kt`: delete `` `test openVirtualPath opens virtual file directly` ``.
- `frontend/.../vfs/KiloVfsManagerTest.kt`: delete the backend/wrapper/adopt tests — `testOpenUsesBackendVirtualFileRpc`, `testOpenClosesMatchingWrapperAfterFrontendHandoff`, `testAdoptConvertsReopenedWrapperIntoRealEditor`, `testAdoptRealKiloFileIsNoop`, `testAdoptWrapperClosesItWhenRealFileAlreadyOpen`. Keep `testOpenUsesRealFileEditorManager`, `testOpeningSamePathDoesNotDuplicate`, `testOpeningSameStableParamsInDifferentOrderDoesNotDuplicate`, `testDistinctPathsCreateDistinctHistoryEntries`, `testCloseDisposesKindDisposable`. Keep the `scope` / `rpc` / `waitFor` / `RemoteKiloFile` setup — Phase 2's sync test (Step 14) needs `scope`/`rpc`/`waitFor`; `RemoteKiloFile` may be deleted if nothing references it after Phase 2.
- `backend/.../rpc/KiloVirtualFileSystemBackendTest.kt`: delete `` `backend editor type id is distinct from frontend` `` and `import ai.kilocode.backend.vfs.KiloBackendFileEditorProvider`. Keep the two decode tests.
- Unchanged (verify they still pass): `KiloFileEditorProviderTest` (incl. `testRestoredAttachmentFileRegistersKindBeforeCreateEditor` — `accept` now ensures the kind), `AttachmentEditorKindTest`, `KiloVirtualFileTest` (asserts `isIncludedInEditorHistory` true — still true by default), `KiloVirtualFileSystemTest`, `KiloVfsTestBase` (registry unchanged).

### Phase 1 build gate (from `packages/kilo-jetbrains/`)

1. `./gradlew :frontend:test --tests "ai.kilocode.client.vfs.*" --tests "ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest" --tests "ai.kilocode.client.app.KiloWorkspaceServiceTest"`
2. `./gradlew :backend:test --tests "ai.kilocode.backend.*"`
3. `./gradlew typecheck`

Must be green before Phase 2.

---

# PHASE 2 — Auto-reopen across restart (backend = source of truth)

Model: backend persists the open kilo path set (host workspace.xml); the frontend pushes the current set on open/close and reopens it on project startup.

### Step 9 — Backend store `backend/.../vfs/KiloVfsOpenStore.kt` (NEW)

Light project service (no XML needed):

```kotlin
package ai.kilocode.backend.vfs

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "KiloVfsOpenFiles", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class KiloVfsOpenStore : PersistentStateComponent<KiloVfsOpenStore.State> {
    data class State(var paths: MutableList<String> = mutableListOf())

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    @Synchronized
    fun replace(paths: List<String>) {
        state = State(paths.toMutableList())
    }

    fun paths(): List<String> = state.paths.toList()
}
```

### Step 10 — RPC methods

`shared/.../rpc/KiloWorkspaceRpcApi.kt` — add:

```kotlin
/** Replace the persisted set of open Kilo virtual paths for [directory]. */
suspend fun setVirtualOpenPaths(directory: String, paths: List<String>)

/** The persisted set of open Kilo virtual paths for [directory]. */
suspend fun virtualOpenPaths(directory: String): List<String>
```

`backend/.../rpc/KiloWorkspaceRpcApiImpl.kt` — add (uses the existing `project(path: Path)` + `file(String)` helpers and the already-imported `com.intellij.openapi.components.service`):

```kotlin
override suspend fun setVirtualOpenPaths(directory: String, paths: List<String>) {
    val project = project(file(directory) ?: return) ?: return
    project.service<KiloVfsOpenStore>().replace(paths)
}

override suspend fun virtualOpenPaths(directory: String): List<String> {
    val project = project(file(directory) ?: return emptyList()) ?: return emptyList()
    return project.service<KiloVfsOpenStore>().paths()
}
```

Add `import ai.kilocode.backend.vfs.KiloVfsOpenStore`.

### Step 11 — Frontend service wrappers `frontend/.../app/KiloWorkspaceService.kt`

Add (mirror the existing `try/catch` + `LOG.warn` style):

```kotlin
fun setVirtualOpenPaths(directory: String, paths: List<String>) {
    cs.launch {
        try {
            call { setVirtualOpenPaths(directory, paths) }
        } catch (e: Exception) {
            LOG.warn("set virtual open paths failed for directory=$directory", e)
        }
    }
}

suspend fun virtualOpenPaths(directory: String): List<String> {
    return try {
        call { virtualOpenPaths(directory) }
    } catch (e: Exception) {
        LOG.warn("virtual open paths lookup failed for directory=$directory", e)
        emptyList()
    }
}
```

### Step 12 — Frontend `KiloVfsManager.sync()` + new listener + restore activity

Add to `KiloVfsManager` (re-add imports `com.intellij.openapi.components.service`, `ai.kilocode.client.app.KiloWorkspaceService`, `kotlinx.coroutines.launch`):

```kotlin
@RequiresEdt
fun sync() {
    val fs = KiloVirtualFileSystem.getInstance()
    val paths = FileEditorManager.getInstance(project).openFiles
        .filterIsInstance<KiloVirtualFile>()
        .map { fs.getPath(it.path) }
    val base = project.basePath ?: return
    cs.launch {
        val dir = service<KiloWorkspaceService>().resolveProjectDirectory(base)
        service<KiloWorkspaceService>().setVirtualOpenPaths(dir, paths)
    }
}
```

New `frontend/.../vfs/KiloVfsOpenTracker.kt` — pushes the set on open/close, suppressed during project close so shutdown doesn't wipe the store:

```kotlin
package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.vfs.VirtualFile

class KiloVfsOpenTracker(private val project: Project) : FileEditorManagerListener {
    @Volatile private var closing = false

    init {
        project.messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
            override fun projectClosing(p: Project) { if (p === project) closing = true }
        })
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file is KiloVirtualFile) project.service<KiloVfsManager>().sync()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!closing && file is KiloVirtualFile) project.service<KiloVfsManager>().sync()
    }
}
```

> Verify in `$INTELLIJ_REPO` that `com.intellij.openapi.project.ProjectCloseListener` with `TOPIC` + `projectClosing(project)` exists in the target platform. If not, use `com.intellij.openapi.project.ProjectManager.TOPIC` + `com.intellij.openapi.project.ProjectManagerListener.projectClosing`.

New `frontend/.../vfs/KiloVfsRestoreActivity.kt` — reopens the persisted set on startup (kinds are ensured by the provider, so no restore race):

```kotlin
package ai.kilocode.client.vfs

import ai.kilocode.client.app.KiloWorkspaceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class KiloVfsRestoreActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val base = project.basePath ?: return
        val ws = service<KiloWorkspaceService>()
        val dir = ws.resolveProjectDirectory(base)
        val paths = ws.virtualOpenPaths(dir)
        if (paths.isEmpty()) return
        ApplicationManager.getApplication().invokeLater({
            val mgr = project.service<KiloVfsManager>()
            paths.forEach { raw ->
                val parsed = KiloVirtualFileSystem.decode(raw) ?: return@forEach
                mgr.openLocal(parsed.kind, parsed.params, focus = false)
            }
        }, project.disposed)
    }
}
```

Reopen uses `openLocal(kind, params)` (re-derives the path with the current `project.locationHash`), so a changed / split-mode hash is irrelevant. Reopening an already-open file is idempotent (`openFile` just focuses), so monolith platform-restore + this activity cannot double a tab.

### Step 13 — `frontend/src/main/resources/kilo.jetbrains.frontend.xml` (Phase 2 wiring)

Re-add, in place of the lines removed in Step 5 — the `postStartupActivity` inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<postStartupActivity implementation="ai.kilocode.client.vfs.KiloVfsRestoreActivity"/>
```

and a new top-level `<projectListeners>` block (sibling of `<extensions>` / `<actions>`):

```xml
<projectListeners>
    <listener class="ai.kilocode.client.vfs.KiloVfsOpenTracker"
              topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"/>
</projectListeners>
```

### Step 14 — Phase 2 tests

- `frontend/.../testing/FakeWorkspaceRpcApi.kt`: add fields + methods:
  ```kotlin
  val openPathPushes = mutableListOf<Pair<String, List<String>>>()
  var openPaths = emptyList<String>()

  override suspend fun setVirtualOpenPaths(directory: String, paths: List<String>) {
      assertNotEdt("setVirtualOpenPaths")
      openPathPushes.add(directory to paths)
  }

  override suspend fun virtualOpenPaths(directory: String): List<String> {
      assertNotEdt("virtualOpenPaths")
      return openPaths
  }
  ```
- `frontend/.../app/KiloWorkspaceServiceTest.kt`: add a test that `virtualOpenPaths` returns the fake's list, and a test that `setVirtualOpenPaths` records a push (poll `rpc.openPathPushes` since the wrapper launches on the scope).
- `frontend/.../vfs/KiloVfsManagerTest.kt` (keep `scope` / `rpc` / `waitFor`): add `testSyncPushesOpenKiloPaths` — open two kilo files via `openLocal`, call `project.service<KiloVfsManager>().sync()` on EDT, `waitFor { rpc.openPathPushes.isNotEmpty() }`, assert the last push's path list contains both canonical paths. (`<projectListeners>` from plugin.xml is not loaded in `BasePlatformTestCase`, so call `sync()` directly.)
- `backend/.../vfs/KiloVfsOpenStoreTest.kt` (NEW, plain class — no fixture): `replace` then `paths` round-trips; a second `replace` overwrites; empty `replace` clears.
- Optional: a restore test that, given `rpc.openPaths = [pathA, pathB]`, the reopen loop (the same `decode → openLocal` loop the activity uses) opens two `KiloVirtualFile`s with the current project hash.

### Phase 2 build gate (from `packages/kilo-jetbrains/`)

1. `./gradlew :frontend:test --tests "ai.kilocode.client.vfs.*" --tests "ai.kilocode.client.app.KiloWorkspaceServiceTest"`
2. `./gradlew :backend:test --tests "ai.kilocode.backend.*"`
3. `./gradlew typecheck`

---

# Final verification

- `./gradlew typecheck` and both `:test` sets all green.
- Confirm no `kilocode_change` markers were added (these JetBrains paths are entirely Kilo-owned).
- Manual split mode (`./gradlew runIdeBackend` + client, or the Split Mode run config): open attachment → exactly one real editor, **no blink, no JSON-named flash**; open 2 → **2** recents (not 4); reopen from Recent Files → real content; **restart with attachments open → they auto-reopen and re-fetch content**; previously-open attachment on startup → **no `Unknown Kilo editor kind` crash**.
- Manual monolith (`./gradlew runIde`): same, plus confirm restart does not produce duplicate tabs.

# Changeset

Rewrite `.changeset/jetbrains-vfs-frontend-handoff.md` (keep the existing front-matter, e.g. `"kilo-code": patch`) to user-facing wording, e.g.: "Fix Kilo attachment editors in JetBrains: opening no longer flickers, Recent Files no longer duplicates entries, and open attachments reload after restarting the IDE."

# Pitfalls / notes

- **`createEditor` `error(...)`**: kept; unreachable for the only real kind (`attachment`, always ensured). Do not convert it to a silent fallback.
- **Shutdown wipe**: the `closing` guard in `KiloVfsOpenTracker.fileClosed` is essential — without it, project teardown fires `fileClosed` for every tab and `sync()` would persist an empty set, breaking reopen. Verify the close topic (Step 12 note).
- **Chatty `resolveProjectDirectory`**: `sync()` resolves the directory per call. Acceptable; if noisy, cache the resolved dir on `KiloVfsManager` after the first resolve.
- **Keep backend VFS registered** (Step 6): removing it would make `kilo://` recents entries show raw JSON names or drop them.
- Do **not** flip the `editor.rd.reopen.editors.on.frontend` registry key; our own restore activity handles split-mode reopen.

# Files summary

Phase 1 — edit: `frontend/.../vfs/KiloFileEditorProvider.kt`, `frontend/.../vfs/KiloVfsManager.kt`, `frontend/.../session/ui/attachment/AttachmentEditorKind.kt`, `frontend/src/main/resources/kilo.jetbrains.frontend.xml`, `backend/src/main/resources/kilo.jetbrains.backend.xml`, `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt`, `shared/.../rpc/KiloWorkspaceRpcApi.kt`, `frontend/.../app/KiloWorkspaceService.kt`, `shared/.../vfs/KiloVirtualFile.kt`. Delete: `backend/.../vfs/KiloBackendFileEditorProvider.kt`, `frontend/.../vfs/KiloVfsReopenListener.kt`. Test edits: `FakeWorkspaceRpcApi.kt`, `KiloWorkspaceServiceTest.kt`, `KiloVfsManagerTest.kt`, `KiloVirtualFileSystemBackendTest.kt`.

Phase 2 — new: `backend/.../vfs/KiloVfsOpenStore.kt`, `frontend/.../vfs/KiloVfsOpenTracker.kt`, `frontend/.../vfs/KiloVfsRestoreActivity.kt`, `backend/.../vfs/KiloVfsOpenStoreTest.kt`. Edit: `shared/.../rpc/KiloWorkspaceRpcApi.kt`, `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt`, `frontend/.../app/KiloWorkspaceService.kt`, `frontend/.../vfs/KiloVfsManager.kt`, `frontend/src/main/resources/kilo.jetbrains.frontend.xml`, `FakeWorkspaceRpcApi.kt`, `KiloWorkspaceServiceTest.kt`, `KiloVfsManagerTest.kt`. Plus the changeset rewrite.
