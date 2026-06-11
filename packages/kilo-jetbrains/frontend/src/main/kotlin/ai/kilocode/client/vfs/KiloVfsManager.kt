package ai.kilocode.client.vfs

import ai.kilocode.client.app.KiloWorkspaceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    private fun file(kind: String, params: Map<String, String>): KiloVirtualFile? {
        return KiloVirtualFileSystem.getInstance().refreshAndFindFileByPath(path(kind, params)) as? KiloVirtualFile
    }

    private fun path(kind: String, params: Map<String, String>): String {
        return KiloVirtualFileSystem.getInstance().getPath(KiloPath(project.locationHash, kind, params))
    }

}
