package ai.kilocode.client.session.ui

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.PromptAttachment
import ai.kilocode.client.session.ui.attachment.AttachmentCard
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.session.ui.prompt.PromptDataKeys
import ai.kilocode.client.session.ui.prompt.PromptPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.SwingUtilities

@Suppress("UnstableApiUsage")
class PromptPanelTest : BasePlatformTestCase() {

    fun `test prompt input uses editor font settings`() {
        val style = SessionEditorStyle.current()
        val panel = PromptPanel(project, { _, _ -> }, {})
        val font = panel.inputFont()

        assertEquals(style.editorFamily, font.name)
        assertEquals(style.editorSize, font.size)
    }

    fun `test prompt input uses editor background`() {
        val style = SessionEditorStyle.current()
        val panel = PromptPanel(project, { _, _ -> }, {})

        assertEquals(style.editorScheme.defaultBackground, panel.defaultFocusedComponent.background)
    }

    fun `test applyStyle updates prompt input and height`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val style = SessionEditorStyle.create(family = "Courier New", size = 26)

        panel.applyStyle(style)

        assertEquals("Courier New", panel.inputFont().name)
        assertEquals(26, panel.inputFont().size)
        assertTrue(panel.preferredSize.height >= 26)
    }

    fun `test prompt editor grows when lines are added`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val editor = panel.defaultFocusedComponent as EditorTextField
        val min = editor.preferredSize.height

        editor.text = "one\ntwo\nthree\nfour\nfive"

        assertTrue(editor.preferredSize.height > min)
    }

    fun `test prompt editor shrinks when lines are removed`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val editor = panel.defaultFocusedComponent as EditorTextField
        val min = editor.preferredSize.height

        editor.text = "one\ntwo\nthree\nfour\nfive"
        assertTrue(editor.preferredSize.height > min)

        editor.text = "one"

        assertEquals(min, editor.preferredSize.height)
    }

    fun `test prompt editor shrinks after clear`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val editor = panel.defaultFocusedComponent as EditorTextField
        val min = editor.preferredSize.height

        editor.text = "one\ntwo\nthree\nfour\nfive"
        assertTrue(editor.preferredSize.height > min)

        panel.clear()

        assertEquals(min, editor.preferredSize.height)
    }

    fun `test attachment only prompt can send`() {
        var sent = false
        val panel = PromptPanel(project, { text, files ->
            sent = text.isBlank() && files.single().url == "file:///tmp/a.png"
        }, {})
        panel.setReady(true)

        panel.addAttachmentForTest(PromptAttachment("a", "a.png", "image/png", "file:///tmp/a.png"))
        panel.send()

        assertTrue(sent)
    }

    fun `test clear removes attachments`() {
        val panel = PromptPanel(project, { _, _ -> }, {})

        panel.addAttachmentForTest(PromptAttachment("a", "a.txt", "text/plain", "file:///tmp/a.txt"))
        assertEquals(1, panel.attachmentCountForTest())

        panel.clear()

        assertEquals(0, panel.attachmentCountForTest())
    }

    fun `test removed attachment can be added again`() {
        val item = PromptAttachment("a", "a.txt", "text/plain", "file:///tmp/a.txt")
        val panel = PromptPanel(project, { _, _ -> }, {})

        panel.addAttachmentForTest(item)
        attachmentRemoveButton(panel, item).doClick()
        panel.addAttachmentForTest(item)

        assertEquals(1, panel.attachmentCountForTest())
    }

    fun `test attachment card is compact icon only with tooltip metadata and hover remove`() {
        val item = PromptAttachment("a", "a.txt", "text/plain", "file:///tmp/a%20b.txt")
        val panel = PromptPanel(project, { _, _ -> }, {})

        panel.addAttachmentForTest(item)

        val button = attachmentRemoveButton(panel, item)
        val card = attachmentCard(panel)

        assertFalse(button.isVisible)
        assertTrue(card.toolTipText.contains("a.txt"))
        assertTrue(card.toolTipText.contains("text/plain"))
        assertTrue(card.toolTipText.contains("/tmp/a b.txt"))
        assertFalse(card.toolTipText.contains("file:///"))
        assertTrue(card.toolTipText.startsWith("<html>"))
        assertTrue(card.toolTipText.contains("Name: a.txt<br>Type: text/plain<br>Location: /tmp/a b.txt"))
        assertFalse(labels(card).any { it.text == "a.txt" || it.text == "text/plain" || it.text == "/tmp/a b.txt" })
        assertTrue(components(card).filterIsInstance<javax.swing.JComponent>().any { it !== button && it.toolTipText == card.toolTipText })
        assertEquals(JBUI.scale(SessionUiStyle.View.Attachment.CARD_WIDTH), card.preferredSize.width)
        assertEquals(JBUI.scale(SessionUiStyle.View.Attachment.CARD_HEIGHT), card.preferredSize.height)
        assertEquals(0, card.getComponentZOrder(button))

        val label = labels(card).first()
        label.dispatchEvent(MouseEvent(label, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 1, 1, 0, false))

        assertTrue(button.isVisible)
        val icon = button.icon
        button.dispatchEvent(MouseEvent(button, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 1, 1, 0, false))
        assertNotSame(icon, button.icon)
        button.dispatchEvent(MouseEvent(button, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, 1, 1, 0, false))
        assertSame(icon, button.icon)
    }

    fun `test attachment child click opens item`() {
        var opened = false
        val card = AttachmentCard(
            ai.kilocode.client.session.ui.attachment.AttachmentCardItem("a.txt", "text/plain", "file:///tmp/a.txt"),
            open = { opened = true },
        )

        val label = labels(card).first()
        label.dispatchEvent(MouseEvent(label, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false))

        assertTrue(opened)
    }

    fun `test reasoning picker hides when variants are empty`() {
        val panel = PromptPanel(project, { _, _ -> }, {})

        panel.reasoning.setItems(emptyList())

        assertFalse(panel.reasoning.isVisible)
    }

    fun `test reasoning picker shows selected variant`() {
        val panel = PromptPanel(project, { _, _ -> }, {})

        panel.reasoning.setItems(listOf(ReasoningPicker.Item("low", "Low"), ReasoningPicker.Item("high", "High")), "high")

        assertTrue(panel.reasoning.isVisible)
        assertEquals("high", panel.reasoning.selectedForTest()?.id)
        assertEquals("High ▾", panel.reasoning.text)
    }

    fun `test reasoning picker aligns unchecked rows`() {
        val picker = ReasoningPicker()
        val low = ReasoningPicker.Item("low", "Low")
        val high = ReasoningPicker.Item("high", "High")

        picker.setItems(listOf(low, high), "high")

        val icon = picker.iconForTest(low)
        assertTrue(icon is EmptyIcon)
        assertSame(AllIcons.Actions.Checked, picker.iconForTest(high))
        assertEquals(AllIcons.Actions.Checked.iconWidth, icon.iconWidth)
        assertEquals(AllIcons.Actions.Checked.iconHeight, icon.iconHeight)
    }

    fun `test reset visibility can be toggled`() {
        val panel = PromptPanel(project, { _, _ -> }, {})

        panel.setResetVisible(true)

        assertTrue(panel.resetVisibleForTest())
    }

    fun `test prompt editor exposes send context`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val sink = TestSink()

        (panel.defaultFocusedComponent as UiDataProvider).uiDataSnapshot(sink)

        assertSame(panel, sink.send)
    }

    fun `test prompt button exposes send context`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val sink = TestSink()

        (panel.buttonForTest() as UiDataProvider).uiDataSnapshot(sink)

        assertSame(panel, sink.send)
    }

    fun `test prompt button switches between send and stop state`() {
        val panel = PromptPanel(project, { _, _ -> }, {})

        assertEquals(KeymapUtil.createTooltipText("Send", "Kilo.SendPrompt"), panel.buttonForTest().toolTipText)
        assertFalse(panel.isStopEnabled)

        panel.setBusy(true)

        assertEquals("Stop", panel.buttonForTest().toolTipText)
        assertTrue(panel.isStopEnabled)
    }

    fun `test auto approve button toggles and updates tooltip`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val button = autoApproveButton(panel)
        var seen: Boolean? = null
        panel.onAutoApproveToggle = { seen = it }

        assertFalse(button.isSelected)
        assertEquals(KiloBundle.message("prompt.action.autoApprove.enable"), button.accessibleContext.accessibleName)
        assertEquals(KiloBundle.message("prompt.action.autoApprove.disabled.tooltip"), button.toolTipText)
        val icon = button.icon

        button.doClick()

        assertEquals(true, seen)

        panel.setAutoApprove(true)

        assertTrue(button.isSelected)
        assertNotSame(icon, button.icon)
        assertEquals(KiloBundle.message("prompt.action.autoApprove.disable"), button.accessibleContext.accessibleName)
        assertEquals(KiloBundle.message("prompt.action.autoApprove.enabled.tooltip"), button.toolTipText)

        button.doClick()

        assertEquals(false, seen)

        panel.setAutoApprove(false)

        assertSame(icon, button.icon)
    }

    fun `test auto approve button sits next to send button`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val auto = autoApproveButton(panel)
        val send = panel.buttonForTest()
        val items = auto.parent.components.toList()

        assertTrue(SwingUtilities.isDescendingFrom(auto, panel.shellForTest()))
        assertSame(auto.parent, send.parent)
        assertEquals(2, items.indexOf(send) - items.indexOf(auto))
    }

    fun `test pickers belong to rounded shell`() {
        val panel = PromptPanel(project, { _, _ -> }, {})
        val shell = panel.shellForTest()

        assertTrue(SwingUtilities.isDescendingFrom(panel.mode, shell))
        assertTrue(SwingUtilities.isDescendingFrom(panel.model, shell))
        assertTrue(SwingUtilities.isDescendingFrom(panel.reasoning, shell))
        assertSame(shell, panel.mode.parent.parent)
    }

    private fun autoApproveButton(panel: PromptPanel): JButton {
        val enable = KiloBundle.message("prompt.action.autoApprove.enable")
        val disable = KiloBundle.message("prompt.action.autoApprove.disable")
        return buttons(panel).first {
            val name = it.accessibleContext.accessibleName
            name == enable || name == disable
        }
    }

    private fun attachmentRemoveButton(panel: PromptPanel, item: PromptAttachment): JButton {
        val name = KiloBundle.message("prompt.attachment.remove", item.name)
        return buttons(panel).first { it.accessibleContext.accessibleName == name }
    }

    private fun attachmentCard(root: java.awt.Component): AttachmentCard {
        fun visit(node: java.awt.Component): AttachmentCard? {
            if (node is AttachmentCard) return node
            if (node is Container) {
                for (child in node.components) {
                    val card = visit(child)
                    if (card != null) return card
                }
            }
            return null
        }
        return visit(root)!!
    }

    private fun buttons(root: java.awt.Component): List<JButton> {
        val out = mutableListOf<JButton>()
        fun visit(node: java.awt.Component) {
            if (node is JButton) out.add(node)
            if (node is Container) node.components.forEach(::visit)
        }
        visit(root)
        return out
    }

    private fun labels(root: java.awt.Component): List<JBLabel> {
        return components(root).filterIsInstance<JBLabel>()
    }

    private fun components(root: java.awt.Component): List<java.awt.Component> {
        val out = mutableListOf<java.awt.Component>()
        fun visit(node: java.awt.Component) {
            out.add(node)
            if (node is Container) node.components.forEach(::visit)
        }
        visit(root)
        return out
    }

    private class TestSink : DataSink {
        var send: Any? = null

        override fun <T : Any> set(key: com.intellij.openapi.actionSystem.DataKey<T>, data: T?) {
            if (key == PromptDataKeys.SEND) send = data
        }

        override fun <T : Any> setNull(key: com.intellij.openapi.actionSystem.DataKey<T>) {
        }

        override fun <T : Any> lazyNull(key: com.intellij.openapi.actionSystem.DataKey<T>) {
        }

        override fun <T : Any> lazyValue(
            key: com.intellij.openapi.actionSystem.DataKey<T>,
            data: (com.intellij.openapi.actionSystem.DataMap) -> T?,
        ) {
        }

        override fun uiDataSnapshot(provider: com.intellij.openapi.actionSystem.UiDataProvider) {
            provider.uiDataSnapshot(this)
        }

        override fun dataSnapshot(provider: com.intellij.openapi.actionSystem.DataSnapshotProvider) {
            provider.dataSnapshot(this)
        }

        override fun uiDataSnapshot(provider: com.intellij.openapi.actionSystem.DataProvider) {
        }
    }

}
