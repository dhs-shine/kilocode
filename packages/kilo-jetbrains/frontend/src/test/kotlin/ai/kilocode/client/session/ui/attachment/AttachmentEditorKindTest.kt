package ai.kilocode.client.session.ui.attachment

import ai.kilocode.client.session.model.FileAttachment
import ai.kilocode.client.vfs.KiloPath
import ai.kilocode.client.vfs.KiloVfsManager
import ai.kilocode.client.vfs.KiloVfsRegistry
import ai.kilocode.client.vfs.KiloVfsTestBase
import ai.kilocode.client.vfs.KiloVirtualFile
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.util.ui.UIUtil

class AttachmentEditorKindTest : KiloVfsTestBase() {
    override fun setUp() {
        super.setUp()
        ensureAttachmentEditorKind()
    }

    override fun tearDown() {
        try {
            service<KiloVfsRegistry>().unregister(AttachmentEditorKind.ID)
        } finally {
            super.tearDown()
        }
    }

    fun testParamsUseStableIdentityOrder() {
        val item = FileAttachment("part1").apply {
            mime = "text/plain"
            url = "data:text/plain;base64,aGVsbG8="
            filename = "note.txt"
        }

        val params = attachmentParams("ses1", "msg1", item, "note.txt", "/repo")

        assertEquals(listOf("sessionId", "messageId", "partId", "filename", "mime", "directory"), params.keys.toList())
        assertEquals("ses1", params["sessionId"])
        assertEquals("msg1", params["messageId"])
        assertEquals("part1", params["partId"])
        assertEquals("note.txt", params["filename"])
        assertEquals("text/plain", params["mime"])
        assertEquals("/repo", params["directory"])
    }

    fun testPresentationUsesAttachmentMetadata() {
        val file = KiloVirtualFile(project, KiloPath("launch", project.locationHash, AttachmentEditorKind.ID, mapOf(
            "sessionId" to "ses1",
            "messageId" to "msg1",
            "partId" to "part1",
            "filename" to "note.txt",
            "mime" to "text/plain",
            "directory" to "/repo",
        )))

        assertTrue(file.isValid)
        assertEquals("note.txt", file.name)
        assertEquals("Kilo / Attachments / ses1 / note.txt", file.presentablePath)
    }

    fun testMissingRequiredParamsInvalidatesFile() {
        val file = KiloVirtualFile(project, KiloPath("launch", project.locationHash, AttachmentEditorKind.ID, mapOf(
            "sessionId" to "ses1",
            "partId" to "part1",
            "filename" to "note.txt",
            "mime" to "text/plain",
            "directory" to "/repo",
        )))

        assertFalse(file.isValid)
    }

    fun testOpeningSameAttachmentParamsDoesNotDuplicate() {
        val params = mapOf(
            "sessionId" to "ses1",
            "messageId" to "msg1",
            "partId" to "part1",
            "filename" to "note.txt",
            "mime" to "text/plain",
            "directory" to "/repo",
        )

        edt {
            project.service<KiloVfsManager>().open(AttachmentEditorKind.ID, params)
            project.service<KiloVfsManager>().open(AttachmentEditorKind.ID, params)
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
            .filter { it.path.kind == AttachmentEditorKind.ID }
        assertEquals(1, files.size)
    }
}
