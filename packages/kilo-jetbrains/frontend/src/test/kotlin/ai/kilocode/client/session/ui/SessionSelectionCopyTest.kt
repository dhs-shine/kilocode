package ai.kilocode.client.session.ui

import ai.kilocode.client.session.SessionUiTestBase
import ai.kilocode.client.session.ui.selection.SessionContextMenu
import ai.kilocode.client.session.views.tool.ShellToolView
import ai.kilocode.client.session.views.tool.ToolView
import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.PartDto
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.text.JTextComponent

@Suppress("UnstableApiUsage")
class SessionSelectionCopyTest : SessionUiTestBase() {
    fun `test transcript view exposes copy provider when selection exists`() {
        val area = showTool("alpha output")

        select(area, "alpha")
        val provider = copyProvider(area)

        assertNotNull(provider)
        assertTrue(provider!!.isCopyEnabled(DataContext.EMPTY_CONTEXT))
    }

    fun `test copy provider writes active selected text`() {
        val area = showTool("alpha output")

        select(area, "alpha")
        copyProvider(area)!!.performCopy(DataContext.EMPTY_CONTEXT)

        assertEquals("alpha", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
    }

    fun `test copy provider writes full component text without selection`() {
        val area = showTool("alpha output")

        copyProvider(area)!!.performCopy(DataContext.EMPTY_CONTEXT)

        assertEquals("alpha output", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
    }

    fun `test selecting another transcript component changes copied text`() {
        val one = showTool("alpha output", id = "tool_a")
        val two = showTool("bravo output", id = "tool_b")

        select(one, "alpha")
        select(two, "bravo")
        copyProvider(two)!!.performCopy(DataContext.EMPTY_CONTEXT)

        assertTrue(one.selectedText.isNullOrEmpty())
        assertEquals("bravo", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
    }

    fun `test code block child context exposes session copy provider`() {
        showText("```text\nalpha code\n```")
        val field = textEditors(ui).first { it.text.contains("alpha code") }
        val editor = field.getEditor(true)!!

        editor.selectionModel.setSelection(0, 5)
        val provider = copyProvider(field as UiDataProvider)
        provider!!.performCopy(DataContext.EMPTY_CONTEXT)

        assertEquals("alpha", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
    }

    fun `test code block child copies full content without selection`() {
        showText("```text\nalpha code\n```")
        val field = textEditors(ui).first { it.text.contains("alpha code") }

        copyProvider(field as UiDataProvider)!!.performCopy(DataContext.EMPTY_CONTEXT)

        assertEquals("alpha code", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
    }

    fun `test markdown popup press keeps selected text copyable`() {
        val area = showTextComponent("alpha output")

        select(area, "alpha")
        popupPress(area)
        copyProvider(area)!!.performCopy(DataContext.EMPTY_CONTEXT)

        assertEquals("alpha", area.selectedText)
        assertEquals("alpha", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
    }

    fun `test session context menu resolves deepest component`() {
        val root = JPanel(null)
        val mid = JPanel(null)
        val child = JPanel(null)
        root.setBounds(0, 0, 100, 100)
        mid.setBounds(10, 10, 80, 80)
        child.setBounds(5, 5, 20, 20)
        root.add(mid)
        mid.add(child)

        assertSame(child, SessionContextMenu.target(root, root, Point(20, 20)))
    }

    private fun select(area: JTextComponent, text: String) {
        val doc = area.document.getText(0, area.document.length)
        val start = doc.indexOf(text)
        assertTrue(start >= 0)
        area.select(start, start + text.length)
    }

    private fun showTool(text: String, id: String = "tool_msg"): JTextComponent {
        if (controller().id == null) showMessages()
        emit(ChatEventDto.MessageUpdated("ses_test", message(id)))
        emit(ChatEventDto.PartUpdated(
            "ses_test",
            PartDto(
                id = "part_$id",
                sessionID = "ses_test",
                messageID = id,
                type = "tool",
                tool = "bash",
                state = "completed",
                input = mapOf("command" to "printf"),
                output = text,
            ),
        ))
        for (view in toolViews(ui)) expand(view)
        layout()
        return textComponent(text)
    }

    private fun showText(text: String) {
        if (controller().id == null) showMessages()
        emit(ChatEventDto.MessageUpdated("ses_test", message("msg_text")))
        emit(ChatEventDto.PartUpdated("ses_test", part("part_text", "msg_text", "text", text)))
        layout()
    }

    private fun showTextComponent(text: String): JTextComponent {
        showText(text)
        return textComponent(text)
    }

    private fun popupPress(area: JTextComponent) {
        val event = MouseEvent(
            area,
            MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            MouseEvent.BUTTON3_DOWN_MASK,
            1,
            1,
            1,
            true,
            MouseEvent.BUTTON3,
        )
        area.dispatchEvent(event)
    }

    private fun toolViews(root: Container): List<Container> {
        val out = mutableListOf<Container>()
        if (root is ShellToolView || root is ToolView) out.add(root)
        for (child in root.components) {
            if (child is Container) out.addAll(toolViews(child))
        }
        return out
    }

    private fun expand(view: Container) = when (view) {
        is ShellToolView -> view.expand()
        is ToolView -> view.expand()
        else -> false
    }

    private fun copyProvider(provider: UiDataProvider): CopyProvider? {
        val sink = CopySink()
        provider.uiDataSnapshot(sink)
        return sink.copy
    }

    private fun copyProvider(component: Component): CopyProvider? {
        (component as? UiDataProvider)?.let(::copyProvider)?.let { return it }
        ancestors(component).filterIsInstance<UiDataProvider>().firstNotNullOfOrNull(::copyProvider)?.let { return it }
        val point = Point((component.width / 2).coerceAtLeast(0), (component.height / 2).coerceAtLeast(0))
        val target = SessionContextMenu.target(ui as JComponent, component, point) ?: component
        ancestors(target).filterIsInstance<UiDataProvider>().firstNotNullOfOrNull(::copyProvider)?.let { return it }
        return providers(ui).firstNotNullOfOrNull(::copyProvider)
    }

    private fun providers(root: Component): Sequence<UiDataProvider> = sequence {
        if (root is UiDataProvider) yield(root)
        if (root is Container) {
            for (child in root.components) yieldAll(providers(child))
        }
    }

    private fun ancestors(component: Component): Sequence<Component> = sequence {
        var comp: Component? = component
        while (comp != null) {
            yield(comp)
            comp = comp.parent
        }
    }

    private fun textEditors(root: Container): List<EditorTextField> {
        val out = mutableListOf<EditorTextField>()
        if (root is EditorTextField) out.add(root)
        for (child in root.components) {
            if (child is JBScrollPane) (child.viewport.view as? EditorTextField)?.let(out::add)
            if (child is Container) out.addAll(textEditors(child))
        }
        return out.distinct()
    }

    private fun textComponent(needle: String): JTextComponent = textComponents(ui)
        .first { it.text.contains(needle) }

    private fun textComponents(root: Container): List<JTextComponent> {
        val out = mutableListOf<JTextComponent>()
        if (root is JTextComponent) out.add(root)
        for (child in root.components) {
            if (child is Container) out.addAll(textComponents(child))
        }
        return out
    }

    private class CopySink : DataSink {
        var copy: CopyProvider? = null

        override fun <T : Any> set(key: DataKey<T>, data: T?) {
            if (key == PlatformDataKeys.COPY_PROVIDER) copy = data as? CopyProvider
        }

        override fun <T : Any> setNull(key: DataKey<T>) {}

        override fun <T : Any> lazyNull(key: DataKey<T>) {}

        override fun <T : Any> lazyValue(key: DataKey<T>, data: (DataMap) -> T?) {}

        override fun uiDataSnapshot(provider: UiDataProvider) = provider.uiDataSnapshot(this)
        override fun dataSnapshot(provider: DataSnapshotProvider) = provider.dataSnapshot(this)
        override fun uiDataSnapshot(provider: DataProvider) {}
    }
}
