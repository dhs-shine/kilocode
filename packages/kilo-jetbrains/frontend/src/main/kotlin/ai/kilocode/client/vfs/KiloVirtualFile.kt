@file:Suppress("LeakingThis")

package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFileBase
import java.io.InputStream
import java.io.OutputStream

class KiloVirtualFile(
    val project: Project,
    val path: KiloPath,
) : LightVirtualFileBase("", null, 0), VirtualFileWithoutContent, VirtualFilePathWrapper, EditorHistoryManager.IncludeInEditorHistoryFile {
    init {
        putUserData(FileEditorManagerKeys.REOPEN_WINDOW, false)
        putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
        isWritable = false
    }

    override fun getFileSystem(): KiloVirtualFileSystem = KiloVirtualFileSystem.getInstance()

    override fun getFileType(): FileType = FileTypes.UNKNOWN

    override fun getPath(): String = fileSystem.getPath(path)

    override fun getName(): String = kind()?.title(project, path.params) ?: path.kind

    override fun getPresentableName(): String = name

    override fun getPresentablePath(): String = kind()?.presentablePath(project, path.params) ?: name

    override fun enforcePresentableName(): Boolean = true

    override fun isValid(): Boolean {
        val kind = kind() ?: return false
        return !project.isDisposed && kind.isValid(project, path.params)
    }

    override fun isPersistedInEditorHistory(): Boolean = false

    override fun getLength(): Long = 0
    override fun contentsToByteArray(): ByteArray = throw UnsupportedOperationException()
    override fun getInputStream(): InputStream = throw UnsupportedOperationException()
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream = throw UnsupportedOperationException()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KiloVirtualFile) return false
        return project == other.project && path == other.path
    }

    override fun hashCode(): Int = 31 * project.hashCode() + path.hashCode()

    private fun kind(): KiloEditorKind? = service<KiloVfsRegistry>().get(path.kind)
}
