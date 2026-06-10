package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager

class KiloVirtualFileTest : KiloVfsTestBase() {
    fun testContentlessPresentationAndUserData() {
        val file = KiloVirtualFile(project, path(mapOf("id" to "7")))
        val included: EditorHistoryManager.IncludeInEditorHistoryFile = file
        val history: EditorHistoryManager.OptionallyIncluded = included
        val err = try {
            file.contentsToByteArray()
            null
        } catch (err: UnsupportedOperationException) {
            err
        }

        assertNotNull(err)
        assertFalse(file.isWritable)
        assertEquals(false, file.getUserData(FileEditorManagerKeys.REOPEN_WINDOW))
        assertEquals(true, file.getUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT))
        assertTrue(history.isIncludedInEditorHistory(project))
        assertFalse(history.isPersistedInEditorHistory())
        assertEquals("Test 7", file.name)
        assertEquals("Test 7", file.presentableName)
        assertEquals("Kilo Test / 7", file.presentablePath)
    }

    fun testValueEqualityUsesProjectAndPath() {
        val one = KiloVirtualFile(project, path(mapOf("id" to "same")))
        val two = KiloVirtualFile(project, path(mapOf("id" to "same")))
        val other = KiloVirtualFile(project, path(mapOf("id" to "other")))

        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
        assertNotSame(one, two)
        assertFalse(one == other)
    }

    fun testValidityReflectsProjectKindAndParams() {
        val valid = KiloVirtualFile(project, path(mapOf("id" to "1")))
        val invalid = KiloVirtualFile(project, path(mapOf("id" to "1", "valid" to "false")))

        assertTrue(valid.isValid)
        assertFalse(invalid.isValid)

        service<KiloVfsRegistry>().unregister(KiloVfsTestKind.ID)
        assertFalse(valid.isValid)
    }
}
