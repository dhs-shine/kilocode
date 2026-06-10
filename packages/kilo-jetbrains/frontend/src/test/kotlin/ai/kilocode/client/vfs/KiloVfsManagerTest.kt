package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil

class KiloVfsManagerTest : KiloVfsTestBase() {
    fun testOpenUsesRealFileEditorManager() {
        val opened = edtValue {
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, mapOf("id" to "11"))
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
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, mapOf("id" to "12"))
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, mapOf("id" to "12"))
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
        assertEquals(1, files.size)
    }

    fun testOpeningSameStableParamsInDifferentOrderDoesNotDuplicate() {
        edt {
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, linkedMapOf("mode" to "tab", "id" to "stable"))
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, linkedMapOf("id" to "stable", "mode" to "tab"))
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
        val history = EditorHistoryManager.getInstance(project).fileList.filterIsInstance<KiloVirtualFile>()
        assertEquals(1, files.size)
        assertEquals(1, history.size)
    }

    fun testDistinctPathsCreateDistinctHistoryEntries() {
        edt {
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, mapOf("id" to "21"))
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, mapOf("id" to "22"))
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
            project.service<KiloVfsManager>().open(KiloVfsTestKind.ID, mapOf("id" to "13"))
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
}
