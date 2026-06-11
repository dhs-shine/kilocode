package ai.kilocode.client.vfs

import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class KiloVfsManagerTest : KiloVfsTestBase() {
    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeWorkspaceRpcApi

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        rpc = FakeWorkspaceRpcApi()
        ApplicationManager.getApplication().replaceService(
            KiloWorkspaceService::class.java,
            KiloWorkspaceService(scope, rpc),
            myFixture.testRootDisposable,
        )
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun testOpenUsesRealFileEditorManager() {
        val opened = edtValue {
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "11"))
        }
        edt { UIUtil.dispatchAllInvocationEvents() }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
        val editor = FileEditorManager.getInstance(project).selectedEditor as KiloFileEditor
        val component = editor.component as JBLabel
        val file = files.single()
        val history = EditorHistoryManager.getInstance(project).fileList

        assertTrue(opened)
        assertEquals(1, files.size)
        assertEquals(KiloVfsTestKind.ID, file.path.kind)
        assertEquals("11", file.path.params["id"])
        assertTrue(history.contains(file))
        assertSame(kind.components.single(), component)
        assertEquals("content:11", component.text)
    }

    fun testOpeningSamePathDoesNotDuplicate() {
        edt {
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "12"))
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "12"))
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
        assertEquals(1, files.size)
    }

    fun testOpeningSameStableParamsInDifferentOrderDoesNotDuplicate() {
        edt {
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, linkedMapOf("mode" to "tab", "id" to "stable"))
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, linkedMapOf("id" to "stable", "mode" to "tab"))
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
        val history = EditorHistoryManager.getInstance(project).fileList.filterIsInstance<KiloVirtualFile>()
        assertEquals(1, files.size)
        assertEquals(1, history.size)
    }

    fun testDistinctPathsCreateDistinctHistoryEntries() {
        edt {
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "21"))
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "22"))
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
        val history = EditorHistoryManager.getInstance(project).fileList.filterIsInstance<KiloVirtualFile>()
        assertEquals(2, files.size)
        assertEquals(2, history.size)
        assertTrue(history.containsAll(files))
    }

    fun testCloseDisposesKindDisposable() {
        edt {
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "13"))
            FileEditorManager.getInstance(project).selectedEditor?.component
            UIUtil.dispatchAllInvocationEvents()
        }
        assertFalse(kind.disposables.single().disposed)

        edt {
            project.service<KiloVfsManager>().close(KiloVfsTestKind.ID, mapOf("id" to "13"))
            UIUtil.dispatchAllInvocationEvents()
        }

        assertTrue(kind.disposables.single().disposed)
    }

    fun testSyncPushesOpenKiloPaths() {
        val fs = KiloVirtualFileSystem.getInstance()
        val expected = listOf(
            fs.getPath(KiloPath(project.locationHash, KiloVfsTestKind.ID, mapOf("id" to "31"))),
            fs.getPath(KiloPath(project.locationHash, KiloVfsTestKind.ID, mapOf("id" to "32"))),
        )

        edt {
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "31"))
            project.service<KiloVfsManager>().openLocal(KiloVfsTestKind.ID, mapOf("id" to "32"))
            project.service<KiloVfsManager>().sync()
        }
        waitFor { rpc.openPathPushes.isNotEmpty() }

        val push = rpc.openPathPushes.last()
        assertEquals(rpc.directory, push.first)
        assertEquals(expected.toSet(), push.second.toSet())
    }

    private fun waitFor(done: () -> Boolean) = runBlocking {
        withTimeout(5_000) {
            while (!edtValue {
                    UIUtil.dispatchAllInvocationEvents()
                    done()
                }) {
                delay(25)
            }
        }
        ApplicationManager.getApplication().invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
    }

}
