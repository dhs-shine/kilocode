package ai.kilocode.client.session.ui.attachment

import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.session.model.FileAttachment
import ai.kilocode.client.testing.FakeSessionRpcApi
import ai.kilocode.client.vfs.KiloPath
import ai.kilocode.client.vfs.KiloVfsManager
import ai.kilocode.client.vfs.KiloVfsRegistry
import ai.kilocode.client.vfs.KiloVfsTestBase
import ai.kilocode.client.vfs.KiloVirtualFile
import ai.kilocode.rpc.dto.MessageDto
import ai.kilocode.rpc.dto.MessageTimeDto
import ai.kilocode.rpc.dto.MessageWithPartsDto
import ai.kilocode.rpc.dto.PartDto
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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

        assertEquals(listOf("sessionId", "messageId", "partId", "attachmentKey", "filename", "mime", "directory"), params.keys.toList())
        assertEquals("ses1", params["sessionId"])
        assertEquals("msg1", params["messageId"])
        assertEquals("part1", params["partId"])
        assertTrue(params["attachmentKey"].isNullOrBlank().not())
        assertEquals("note.txt", params["filename"])
        assertEquals("text/plain", params["mime"])
        assertEquals("/repo", params["directory"])
        assertFalse(params.keys.any { it.contains("launch", ignoreCase = true) })
        assertFalse(params.keys.any { it.contains("time", ignoreCase = true) })
        assertFalse(params.keys.any { it.contains("random", ignoreCase = true) })
    }

    fun testPresentationUsesAttachmentMetadata() {
        val file = KiloVirtualFile(project, KiloPath(project.locationHash, AttachmentEditorKind.ID, mapOf(
            "sessionId" to "ses1",
            "messageId" to "msg1",
            "partId" to "part1",
            "filename" to "note.txt",
            "mime" to "text/plain",
            "directory" to "/repo",
        )))

        assertTrue(file.isValid)
        assertEquals("note.txt", file.name)
        assertEquals("Kilo / attachment / ses1 / note.txt", file.presentablePath)
        assertEquals("note.txt", AttachmentEditorKind.title(project, file.path.params))
        assertEquals("Kilo / Attachments / ses1 / note.txt", AttachmentEditorKind.presentablePath(project, file.path.params))
    }

    fun testMissingRequiredParamsInvalidatesFile() {
        val file = KiloVirtualFile(project, KiloPath(project.locationHash, AttachmentEditorKind.ID, mapOf(
            "sessionId" to "ses1",
            "partId" to "part1",
            "filename" to "note.txt",
            "mime" to "text/plain",
            "directory" to "/repo",
        )))

        assertTrue(file.isValid)
        assertFalse(AttachmentEditorKind.isValid(project, file.path.params))
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
            project.service<KiloVfsManager>().openLocal(AttachmentEditorKind.ID, params)
            project.service<KiloVfsManager>().openLocal(AttachmentEditorKind.ID, params)
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
            .filter { it.path.kind == AttachmentEditorKind.ID }
        assertEquals(1, files.size)
    }

    fun testDuplicatePartAttachmentsUseDistinctHistoryEntries() {
        val first = FileAttachment("part1").apply {
            mime = "text/plain"
            url = "data:text/plain;base64,b25l"
            filename = "note.txt"
        }
        val second = FileAttachment("part1").apply {
            mime = "text/plain"
            url = "data:text/plain;base64,dHdv"
            filename = "note.txt"
        }
        val one = attachmentParams("ses1", "msg1", first, "note.txt", "/repo")
        val two = attachmentParams("ses1", "msg1", second, "note.txt", "/repo")

        assertFalse(one == two)
        assertFalse(one["attachmentKey"] == two["attachmentKey"])

        edt {
            project.service<KiloVfsManager>().openLocal(AttachmentEditorKind.ID, one)
            project.service<KiloVfsManager>().openLocal(AttachmentEditorKind.ID, two)
            UIUtil.dispatchAllInvocationEvents()
        }

        val files = FileEditorManager.getInstance(project).openFiles.filterIsInstance<KiloVirtualFile>()
            .filter { it.path.kind == AttachmentEditorKind.ID }
        val history = EditorHistoryManager.getInstance(project).fileList.filterIsInstance<KiloVirtualFile>()
            .filter { it.path.kind == AttachmentEditorKind.ID }
        assertEquals(2, files.size)
        assertEquals(2, history.size)
        assertTrue(history.containsAll(files))

        edt {
            files.forEach { FileEditorManager.getInstance(project).closeFile(it) }
            UIUtil.dispatchAllInvocationEvents()
        }

        val closed = EditorHistoryManager.getInstance(project).fileList.filterIsInstance<KiloVirtualFile>()
            .filter { it.path.kind == AttachmentEditorKind.ID }
        assertEquals(2, closed.size)
        assertEquals(2, closed.map { it.path }.distinct().size)
        assertTrue(closed.any { it.path.params["attachmentKey"] == one["attachmentKey"] })
        assertTrue(closed.any { it.path.params["attachmentKey"] == two["attachmentKey"] })
    }

    @Suppress("UnstableApiUsage")
    fun testFetchUsesAttachmentKeyBeforeDuplicatePartId() {
        val cs = CoroutineScope(SupervisorJob())
        val rpc = FakeSessionRpcApi()
        val first = PartDto(
            id = "part1",
            sessionID = "ses1",
            messageID = "msg1",
            type = "file",
            mime = "text/plain",
            url = "data:text/plain;base64,b25l",
            filename = "note.txt",
        )
        val second = first.copy(url = "data:text/plain;base64,dHdv")
        rpc.history.add(MessageWithPartsDto(
            info = MessageDto(
                id = "msg1",
                sessionID = "ses1",
                role = "user",
                time = MessageTimeDto(created = 0.0),
            ),
            parts = listOf(first, second),
        ))
        project.replaceService(KiloSessionService::class.java, KiloSessionService(project, cs, rpc), testRootDisposable)
        val item = FileAttachment("part1").apply {
            mime = "text/plain"
            url = second.url.orEmpty()
            filename = "note.txt"
        }
        var result: AttachmentData? = null
        val parent = Disposer.newDisposable()

        try {
            KiloAttachmentEditorService(project, cs).load(attachmentParams("ses1", "msg1", item, "note.txt", "/repo"), parent) {
                result = it
            }

            val until = System.currentTimeMillis() + 5_000
            while (result == null && System.currentTimeMillis() < until) {
                UIUtil.dispatchAllInvocationEvents()
                Thread.sleep(50)
            }
            val data = result
            assertTrue(data is AttachmentData.Text)
            assertEquals("two", (data as AttachmentData.Text).text)
        } finally {
            Disposer.dispose(parent)
            cs.cancel()
        }
    }
}
