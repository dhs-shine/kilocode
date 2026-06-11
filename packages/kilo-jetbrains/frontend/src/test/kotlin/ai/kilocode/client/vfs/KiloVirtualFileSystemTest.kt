package ai.kilocode.client.vfs

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class KiloVirtualFileSystemTest : KiloVfsTestBase() {
    fun testPathRoundTripAndFindFile() {
        val fs = KiloVirtualFileSystem.getInstance()
        val raw = fs.getPath(path(mapOf("id" to "42", "mode" to "tab")))
        val file = fs.findFileByPath(raw) as KiloVirtualFile

        assertEquals(KiloVfsTestKind.ID, file.path.kind)
        assertEquals("42", file.path.params["id"])
        assertEquals("tab", file.path.params["mode"])
        assertEquals(project, file.project)
    }

    fun testDecodesRawAndUrlShapedPaths() {
        val fs = KiloVirtualFileSystem.getInstance()
        val raw = fs.getPath(path(mapOf("id" to "url", "mode" to "tab")))
        val encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8)
        val path = KiloVirtualFileSystem.decode(raw)
        val url = KiloVirtualFileSystem.decode("${KiloVirtualFileSystem.PROTOCOL}://$raw")
        val escaped = KiloVirtualFileSystem.decode("${KiloVirtualFileSystem.PROTOCOL}://$encoded")
        val file = fs.findFileByPath("${KiloVirtualFileSystem.PROTOCOL}://$raw") as KiloVirtualFile

        assertEquals(path, url)
        assertEquals(path, escaped)
        assertEquals("url", file.path.params["id"])
        assertEquals(project, file.project)
    }

    fun testSerializedPathUsesStableCanonicalParams() {
        val fs = KiloVirtualFileSystem.getInstance()
        val one = fs.getPath(path(linkedMapOf("mode" to "tab", "id" to "42")))
        val two = fs.getPath(path(linkedMapOf("id" to "42", "mode" to "tab")))
        val file = fs.findFileByPath(one) as KiloVirtualFile

        assertEquals(one, two)
        assertFalse(one.contains("launchId"))
        assertFalse(one.contains("launch"))
        assertEquals(listOf("id", "mode"), file.path.params.keys.toList())
    }

    fun testUnknownKindStillCreatesGenericVirtualFile() {
        val fs = KiloVirtualFileSystem.getInstance()
        val raw = fs.getPath(path(kind = "missing"))
        val file = fs.findFileByPath(raw) as KiloVirtualFile

        assertEquals("missing", file.path.kind)
        assertEquals(project, file.project)
    }

    fun testImageLikeKiloFileKeepsUnknownAssignedFileType() {
        val file = KiloVirtualFile(project, path(mapOf("id" to "png", "filename" to "screen.png")))
        val registry = FileTypeRegistry.getInstance()

        assertEquals(FileTypes.UNKNOWN, registry.getFileTypeByFile(file))
        assertTrue(registry.isFileOfType(file, FileTypes.UNKNOWN))
    }

    fun testMalformedPathReturnsNull() {
        assertNull(KiloVirtualFileSystem.getInstance().findFileByPath("not-json"))
    }
}
