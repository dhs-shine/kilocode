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

    fun testUnknownKindReturnsNull() {
        val fs = KiloVirtualFileSystem.getInstance()
        val raw = fs.getPath(path(kind = "missing"))

        assertNull(fs.findFileByPath(raw))
    }

    fun testMalformedPathReturnsNull() {
        assertNull(KiloVirtualFileSystem.getInstance().findFileByPath("not-json"))
    }
}
