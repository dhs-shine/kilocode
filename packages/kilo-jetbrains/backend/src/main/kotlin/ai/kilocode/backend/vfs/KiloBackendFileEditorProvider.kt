package ai.kilocode.backend.vfs

import ai.kilocode.client.files.KiloAttachmentFileType
import ai.kilocode.log.KiloLog
import com.intellij.idea.AppMode
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class KiloBackendFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!AppMode.isRemoteDevHost()) return false
        val ok = file.fileType == KiloAttachmentFileType.INSTANCE
        if (ok) {
            LOG.info(
                "kind=kilo-backend-editor-provider phase=accept ok=true file=${file.javaClass.name} protocol=${file.fileSystem.protocol} " +
                    "project=${project.name} hash=${project.locationHash} name=${file.name}"
            )
        }
        return ok
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        LOG.info(
            "kind=kilo-backend-editor-provider phase=create file=${file.javaClass.name} protocol=${file.fileSystem.protocol} " +
                "project=${project.name} hash=${project.locationHash} name=${file.name}"
        )
        return KiloBackendFileEditor(file)
    }

    override fun disposeEditor(editor: FileEditor) {
        Disposer.dispose(editor)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "KiloAttachmentEditorBackend"
        private val LOG = KiloLog.create(KiloBackendFileEditorProvider::class.java)
    }
}

private class KiloBackendFileEditor(
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val component = JPanel()

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = file.presentableName
    override fun getFile(): VirtualFile = file
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun dispose() {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}
