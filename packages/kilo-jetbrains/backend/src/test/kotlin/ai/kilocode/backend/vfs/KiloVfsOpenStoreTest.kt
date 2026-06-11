package ai.kilocode.backend.vfs

import kotlin.test.Test
import kotlin.test.assertEquals

class KiloVfsOpenStoreTest {
    @Test
    fun `replace round trips paths`() {
        val store = KiloVfsOpenStore()

        store.replace(listOf("path-a", "path-b"))

        assertEquals(listOf("path-a", "path-b"), store.paths())
    }

    @Test
    fun `replace overwrites previous paths`() {
        val store = KiloVfsOpenStore()

        store.replace(listOf("path-a", "path-b"))
        store.replace(listOf("path-c"))

        assertEquals(listOf("path-c"), store.paths())
    }

    @Test
    fun `empty replace clears paths`() {
        val store = KiloVfsOpenStore()

        store.replace(listOf("path-a"))
        store.replace(emptyList())

        assertEquals(emptyList(), store.paths())
    }
}
