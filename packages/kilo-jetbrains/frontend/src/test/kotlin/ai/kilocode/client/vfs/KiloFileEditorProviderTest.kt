package ai.kilocode.client.vfs

import ai.kilocode.client.session.model.FileAttachment
import ai.kilocode.client.session.ui.attachment.AttachmentEditorKind
import ai.kilocode.client.session.ui.attachment.attachmentParams
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.components.JBLabel
import java.io.InputStream
import java.io.OutputStream

class KiloFileEditorProviderTest : KiloVfsTestBase() {
    fun testAcceptsOnlyRegisteredKiloFiles() {
        val provider = KiloFileEditorProvider()

        assertTrue(provider.accept(project, KiloVirtualFile(project, path())))
        assertFalse(provider.accept(project, KiloVirtualFile(project, path(kind = "missing"))))
        assertFalse(provider.accept(project, LightVirtualFile("plain.txt")))
    }

    fun testAcceptsDecodableKiloProtocolFile() {
        val provider = KiloFileEditorProvider()
        val raw = KiloVirtualFileSystem.getInstance().getPath(path(mapOf("id" to "remote", "filename" to "screen.png")))
        val file = RemoteKiloFile(raw, "screen.png")

        assertTrue(provider.accept(project, file))

        val editor = edtValue { provider.createEditor(project, file) as KiloFileEditor }
        val component = edtValue { editor.component }

        assertSame(file, editor.file)
        assertEquals("content:remote", (component as JBLabel).text)
    }

    fun testAcceptsUrlShapedKiloProtocolFile() {
        val provider = KiloFileEditorProvider()
        val raw = KiloVirtualFileSystem.getInstance().getPath(path(mapOf("id" to "url", "filename" to "screen.png")))
        val file = RemoteKiloFile("backend-wrapper", "screen.png", "${KiloVirtualFileSystem.PROTOCOL}://$raw")

        assertTrue(provider.accept(project, file))

        val editor = edtValue { provider.createEditor(project, file) as KiloFileEditor }
        val component = edtValue { editor.component }

        assertSame(file, editor.file)
        assertEquals("content:url", (component as JBLabel).text)
    }

    fun testRestoredAttachmentFileRegistersKindBeforeCreateEditor() {
        service<KiloVfsRegistry>().unregister(AttachmentEditorKind.ID)
        val provider = KiloFileEditorProvider()
        val item = FileAttachment("part1").apply {
            mime = "text/plain"
            url = "data:text/plain;base64,aGVsbG8="
            filename = "note.txt"
        }
        val raw = KiloVirtualFileSystem.getInstance().getPath(KiloPath(
            project.locationHash,
            AttachmentEditorKind.ID,
            attachmentParams("ses1", "msg1", item, "note.txt", "/repo"),
        ))
        val file = RemoteKiloFile("backend-wrapper", "note.txt", "${KiloVirtualFileSystem.PROTOCOL}://$raw")

        assertTrue(provider.accept(project, file))

        val editor = edtValue { provider.createEditor(project, file) as KiloFileEditor }

        assertSame(file, editor.file)
        assertEquals("note.txt", editor.name)
        edtValue { editor.component }
    }

    fun testCreateEditorBuildsKindComponent() {
        val provider = KiloFileEditorProvider()
        val file = KiloVirtualFile(project, path(mapOf("id" to "9")))
        val editor = edtValue { provider.createEditor(project, file) as KiloFileEditor }
        val component = edtValue { editor.component }

        assertSame(kind.components.single(), component)
        assertEquals("content:9", (component as JBLabel).text)
        assertEquals("KiloVfsEditor", provider.editorTypeId)
        assertEquals(FileEditorPolicy.HIDE_OTHER_EDITORS, provider.policy)
        service<KiloVfsRegistry>().unregister(KiloVfsTestKind.ID)
        assertFalse(provider.accept(project, file))
    }

    fun testKiloProviderHidesCompetingEditorsForImageLikeNames() {
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(CompetingProvider(), myFixture.testRootDisposable)
        val file = KiloVirtualFile(project, path(mapOf("id" to "png", "filename" to "screen.png")))

        val providers = FileEditorProviderManager.getInstance().getProviderList(project, file)

        assertEquals(listOf(KiloFileEditorProvider.EDITOR_TYPE_ID), providers.map { it.editorTypeId })
    }

    private class CompetingProvider : FileEditorProvider, DumbAware {
        override fun accept(project: Project, file: VirtualFile): Boolean = file is KiloVirtualFile

        override fun acceptRequiresReadAction(): Boolean = false

        override fun createEditor(project: Project, file: VirtualFile): FileEditor = error("Competing editor should be hidden")

        override fun disposeEditor(editor: FileEditor) {
            Disposer.dispose(editor)
        }

        override fun getEditorTypeId(): String = "CompetingKiloEditor"

        override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
    }

    private class RemoteKiloFile(
        private val raw: String,
        name: String,
        private val link: String = "${KiloVirtualFileSystem.PROTOCOL}://$raw",
    ) : LightVirtualFileBase(name, FileTypes.UNKNOWN, 0) {
        override fun getFileSystem(): VirtualFileSystem = KiloVirtualFileSystem.getInstance()

        override fun getPath(): String = raw

        override fun getUrl(): String = link

        override fun getLength(): Long = 0

        override fun contentsToByteArray(): ByteArray = throw UnsupportedOperationException()

        override fun getInputStream(): InputStream = throw UnsupportedOperationException()

        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
            throw UnsupportedOperationException()
        }
    }
}
