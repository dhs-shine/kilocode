package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBLabel

class KiloFileEditorProviderTest : KiloVfsTestBase() {
    fun testAcceptsOnlyRegisteredKiloFiles() {
        val provider = KiloFileEditorProvider()

        assertTrue(provider.accept(project, KiloVirtualFile(project, path())))
        assertFalse(provider.accept(project, KiloVirtualFile(project, path(kind = "missing"))))
        assertFalse(provider.accept(project, LightVirtualFile("plain.txt")))
    }

    fun testCreateEditorBuildsKindComponent() {
        val provider = KiloFileEditorProvider()
        val file = KiloVirtualFile(project, path(mapOf("id" to "9")))
        val editor = edtValue { provider.createEditor(project, file) as KiloFileEditor }
        val component = edtValue { editor.component }

        assertSame(kind.components.single(), component)
        assertEquals("content:9", (component as JBLabel).text)
        assertEquals("KiloVfsEditor", provider.editorTypeId)
        service<KiloVfsRegistry>().unregister(KiloVfsTestKind.ID)
        assertFalse(provider.accept(project, file))
    }
}
