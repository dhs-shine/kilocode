package ai.kilocode.client.vfs

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

    fun testUnknownKindReturnsNull() {
        val fs = KiloVirtualFileSystem.getInstance()
        val raw = fs.getPath(path(kind = "missing"))

        assertNull(fs.findFileByPath(raw))
    }

    fun testMalformedPathReturnsNull() {
        assertNull(KiloVirtualFileSystem.getInstance().findFileByPath("not-json"))
    }
}
