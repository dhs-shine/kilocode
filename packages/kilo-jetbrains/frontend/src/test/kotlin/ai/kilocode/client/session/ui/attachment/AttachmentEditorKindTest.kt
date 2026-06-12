package ai.kilocode.client.session.ui.attachment

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.files.KiloEditorFileDescriptor
import ai.kilocode.client.files.KiloEditorFileDescriptors
import ai.kilocode.client.session.model.FileAttachment
import ai.kilocode.client.testing.FakeAppRpcApi
import ai.kilocode.client.testing.FakeSessionRpcApi
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.MessageDto
import ai.kilocode.rpc.dto.MessageTimeDto
import ai.kilocode.rpc.dto.MessageWithPartsDto
import ai.kilocode.rpc.dto.PartDto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class AttachmentEditorKindTest : BasePlatformTestCase() {
    fun testDescriptorUsesStableIdentityFields() {
        val item = FileAttachment("part1").apply {
            mime = "text/plain"
            url = "data:text/plain;base64,aGVsbG8="
            filename = "note.txt"
        }

        val descriptor = attachmentDescriptor("ses1", "msg1", item, "note.txt", "/repo")
        val json = KiloEditorFileDescriptors.encode(descriptor)
        val decoded = KiloEditorFileDescriptors.decode(json)

        assertEquals(descriptor, decoded)
        assertTrue(descriptor.validate())
        assertEquals(KiloEditorFileDescriptor.VERSION, descriptor.version)
        assertEquals(KiloEditorFileDescriptor.SESSION_ATTACHMENT, descriptor.kind)
        assertEquals("ses1", descriptor.sessionId)
        assertEquals("msg1", descriptor.messageId)
        assertEquals("part1", descriptor.partId)
        assertFalse(descriptor.attachmentKey.isNullOrBlank())
        assertEquals("note.txt", descriptor.filename)
        assertEquals("text/plain", descriptor.mime)
        assertEquals("/repo", descriptor.directory)
        assertFalse(json.contains("launch", ignoreCase = true))
        assertFalse(json.contains("time", ignoreCase = true))
        assertFalse(json.contains("random", ignoreCase = true))
    }

    fun testSameDescriptorMapsToSamePhysicalPath() {
        val descriptor = KiloEditorFileDescriptor.attachment("ses1", "msg1", "part1", "key1", "note.txt", "text/plain", "/repo")
        val root = Path.of("/system/kilo/editors")

        val one = KiloEditorFileDescriptors.path(root, descriptor)
        val two = KiloEditorFileDescriptors.path(root, KiloEditorFileDescriptors.decode(KiloEditorFileDescriptors.encode(descriptor)))

        assertEquals(one, two)
        assertTrue(one.toString().contains("/system/kilo/editors/session-attachments/"))
        assertTrue(one.fileName.toString().startsWith("attachment__note.txt__ses_ses1__msg_msg1__part_part1__"))
        assertTrue(one.fileName.toString().endsWith(".kiloattachment"))
    }

    fun testDuplicatePartAttachmentsMapToDistinctFiles() {
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
        val one = attachmentDescriptor("ses1", "msg1", first, "note.txt", "/repo")
        val two = attachmentDescriptor("ses1", "msg1", second, "note.txt", "/repo")
        val root = Path.of("/system/kilo/editors")

        assertFalse(one == two)
        assertFalse(one.attachmentKey == two.attachmentKey)
        assertFalse(KiloEditorFileDescriptors.path(root, one) == KiloEditorFileDescriptors.path(root, two))
    }

    @Suppress("UnstableApiUsage")
    fun testFetchUsesAttachmentKeyBeforeDuplicatePartId() {
        val cs = CoroutineScope(SupervisorJob())
        val app = FakeAppRpcApi()
        val rpc = FakeSessionRpcApi()
        app.state.value = KiloAppStateDto(KiloAppStatusDto.READY)
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
        ApplicationManager.getApplication().replaceService(KiloAppService::class.java, KiloAppService(cs, app), testRootDisposable)
        project.replaceService(KiloSessionService::class.java, KiloSessionService(project, cs, rpc), testRootDisposable)
        val item = FileAttachment("part1").apply {
            mime = "text/plain"
            url = second.url.orEmpty()
            filename = "note.txt"
        }
        val results = mutableListOf<AttachmentData>()
        val parent = Disposer.newDisposable()

        try {
            KiloAttachmentEditorService(project, cs).load(attachmentDescriptor("ses1", "msg1", item, "note.txt", "/repo"), parent) {
                results.add(it)
            }

            waitFor { results.any { it is AttachmentData.Text } }
            assertTrue(results.any { it is AttachmentData.Connecting })
            val data = results.last { it is AttachmentData.Text } as AttachmentData.Text
            assertEquals("two", data.text)
        } finally {
            Disposer.dispose(parent)
            cs.cancel()
        }
    }

    @Suppress("UnstableApiUsage")
    fun testLoadShowsConnectionFailedUntilRetryBecomesReady() = runBlocking {
        val cs = CoroutineScope(SupervisorJob())
        val app = FakeAppRpcApi()
        val rpc = FakeSessionRpcApi()
        val part = PartDto(
            id = "part1",
            sessionID = "ses1",
            messageID = "msg1",
            type = "file",
            mime = "text/plain",
            url = "data:text/plain;base64,b2s=",
            filename = "note.txt",
        )
        rpc.history.add(MessageWithPartsDto(
            info = MessageDto(
                id = "msg1",
                sessionID = "ses1",
                role = "user",
                time = MessageTimeDto(created = 0.0),
            ),
            parts = listOf(part),
        ))
        app.state.value = KiloAppStateDto(KiloAppStatusDto.ERROR)
        ApplicationManager.getApplication().replaceService(KiloAppService::class.java, KiloAppService(cs, app), testRootDisposable)
        project.replaceService(KiloSessionService::class.java, KiloSessionService(project, cs, rpc), testRootDisposable)
        val item = FileAttachment("part1").apply {
            mime = part.mime.orEmpty()
            url = part.url.orEmpty()
            filename = part.filename.orEmpty()
        }
        val results = mutableListOf<AttachmentData>()
        val parent = Disposer.newDisposable()

        try {
            KiloAttachmentEditorService(project, cs).load(attachmentDescriptor("ses1", "msg1", item, "note.txt", "/repo"), parent) {
                results.add(it)
            }

            waitFor { results.any { it is AttachmentData.ConnectionFailed } }
            assertTrue(results.any { it is AttachmentData.Connecting })
            assertTrue(results.any { it is AttachmentData.ConnectionFailed })

            app.state.value = KiloAppStateDto(KiloAppStatusDto.READY)

            waitFor { results.any { it is AttachmentData.Text } }
            val data = results.last { it is AttachmentData.Text } as AttachmentData.Text
            assertEquals("ok", data.text)
        } finally {
            Disposer.dispose(parent)
            cs.cancel()
        }
    }

    private fun waitFor(done: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < until) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(50)
        }
        assertTrue(done())
    }
}
