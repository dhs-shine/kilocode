package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.toolKind
import ai.kilocode.client.session.views.base.SecondarySessionPartView
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.ScrollPaneConstants

@Suppress("UnstableApiUsage")
class ReadToolViewTest : BasePlatformTestCase() {

    fun `test read tool shows filename`() {
        val t = tool().also { it.input = mapOf("filePath" to "README.MD") }

        val view = ReadToolView(t)
        val base: Any = view

        assertTrue(base is SecondarySessionPartView)
        assertTrue(view.labelText().contains("Read"))
        assertTrue(view.labelText().contains("README.MD"))
    }

    fun `test read tool handles windows path`() {
        val t = tool().also { it.input = mapOf("filePath" to "C:\\repo\\README.MD") }

        val view = ReadToolView(t)

        assertTrue(view.labelText().contains("README.MD"))
    }

    fun `test read output is secondary collapsible body`() {
        val t = tool().also { it.output = "file contents" }
        val view = ReadToolView(t)

        assertTrue(view.hasToggle())
        assertFalse(view.isExpanded())
        assertFalse(view.bodyVisible())
        assertEquals("file contents", view.bodyText())
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, view.horizontalPolicy())

        view.toggle()

        assertTrue(view.isExpanded())
        assertTrue(view.bodyVisible())
    }

    fun `test view factory routes read to read tool view`() {
        assertTrue(ViewFactory.create(tool(), openFile = {}) is ReadToolView)
    }

    fun `test canRender matches read tools only`() {
        assertTrue(ReadToolView.canRender(tool()))
        assertFalse(ReadToolView.canRender(Tool("p2", "grep", toolKind("grep"))))
    }

    private fun tool() = Tool("p1", "read", toolKind("read")).also { it.state = ToolExecState.COMPLETED }
}
