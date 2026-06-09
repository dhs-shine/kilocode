package ai.kilocode.client.vfs

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
        return file is KiloVirtualFile && service<KiloVfsRegistry>().get(file.path.kind) != null
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val kilo = file as KiloVirtualFile
        val kind = service<KiloVfsRegistry>().get(kilo.path.kind) ?: error("Unknown Kilo editor kind: ${kilo.path.kind}")
        return KiloFileEditor(project, kilo, kind)
    }

    override fun disposeEditor(editor: FileEditor) {
        Disposer.dispose(editor)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE

    companion object {
        const val EDITOR_TYPE_ID = "KiloVfsEditor"
    }
}
