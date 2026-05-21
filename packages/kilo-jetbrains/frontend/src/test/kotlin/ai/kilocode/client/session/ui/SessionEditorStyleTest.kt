package ai.kilocode.client.session.ui

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

@Suppress("UnstableApiUsage")
class SessionEditorStyleTest : BasePlatformTestCase() {

    fun `test transcript font uses editor settings`() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val style = SessionEditorStyle.current()
        val font = style.transcriptFont

        assertEquals(scheme.editorFontName, font.name)
        assertEquals(scheme.editorFontSize, font.size)
        assertEquals(scheme.defaultForeground, style.editorForeground)
        assertEquals(scheme.defaultBackground, style.editorBackground)
        assertEquals(Font.PLAIN, font.style)
    }

    fun `test bold editor font uses editor family and size`() {
        val style = SessionEditorStyle.current()
        val font = style.boldEditorFont

        assertEquals(style.editorFamily, font.name)
        assertEquals(style.editorSize, font.size)
        assertTrue(font.isBold)
    }

    fun `test small editor font uses editor family with smaller editor-derived size`() {
        val style = SessionEditorStyle.current()
        val font = style.smallEditorFont

        assertEquals(style.editorFamily, font.name)
        assertTrue(font.size < style.editorSize)
    }

    fun `test custom style derives fonts from supplied editor baseline`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)

        assertEquals("Courier New", style.editorFamily)
        assertEquals(22, style.editorSize)
        assertEquals("Courier New", style.transcriptFont.name)
        assertEquals(22, style.transcriptFont.size)
        assertEquals("Courier New", style.boldEditorFont.name)
        assertEquals(22, style.boldEditorFont.size)
        assertTrue(style.boldEditorFont.isBold)
        assertTrue(style.smallEditorFont.size < style.editorSize)
        assertEquals(style.editorSize, style.uiFont.size)
    }

    fun `test ui fonts use platform label family not editor family`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)

        // uiFont / boldUiFont / smallUiFont must NOT use the editor font family
        assertFalse("uiFont should not use editor font family", style.uiFont.name == "Courier New")
        assertFalse("boldUiFont should not use editor font family", style.boldUiFont.name == "Courier New")
        assertFalse("smallUiFont should not use editor font family", style.smallUiFont.name == "Courier New")
    }

    fun `test ui fonts inherit editor size`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)

        assertEquals("uiFont size should match editor size", 22, style.uiFont.size)
        assertEquals("boldUiFont size should match editor size", 22, style.boldUiFont.size)
        assertTrue("boldUiFont should be bold", style.boldUiFont.isBold)
        assertTrue("smallUiFont should be smaller than editor size", style.smallUiFont.size < style.editorSize)
    }
}
