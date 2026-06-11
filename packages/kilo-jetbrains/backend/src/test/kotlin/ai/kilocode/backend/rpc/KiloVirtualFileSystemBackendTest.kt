package ai.kilocode.backend.rpc

import ai.kilocode.client.vfs.KiloPath
import ai.kilocode.client.vfs.KiloVirtualFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KiloVirtualFileSystemBackendTest {
    @Test
    fun `backend decodes frontend built virtual path with canonical params`() {
        val fs = KiloVirtualFileSystem()
        val raw = fs.getPath(KiloPath(
            projectHash = "frontend-project",
            kind = "attachment",
            params = linkedMapOf(
                "sessionId" to "ses1",
                "messageId" to "msg1",
                "partId" to "part1",
                "attachmentKey" to "key1",
                "filename" to "note.txt",
                "mime" to "text/plain",
                "directory" to "/repo",
            ),
        ))

        val path = KiloVirtualFileSystem.decode(raw)

        assertNotNull(path)
        assertEquals("frontend-project", path.projectHash)
        assertEquals("attachment", path.kind)
        assertEquals(listOf("attachmentKey", "directory", "filename", "messageId", "mime", "partId", "sessionId"), path.params.keys.toList())
        assertEquals("key1", path.params["attachmentKey"])
    }

    @Test
    fun `backend rejects malformed virtual path`() {
        assertNull(KiloVirtualFileSystem.decode("not-json"))
    }
}
