package ai.kilocode.client.settings.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import ai.kilocode.client.ui.UiStyle
import java.awt.Container
import java.awt.Rectangle
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class SettingsRowsTest : BasePlatformTestCase() {

    fun `test rows do not insert separators`() {
        val rows = SettingsRows()

        rows.row(SettingsRow("One", value = JButton("A")))
        rows.row(SettingsRow("Two", value = JButton("B")))

        assertEquals(2, rows.componentCount)
        assertTrue(components(rows).none { it is SeparatorComponent })
    }

    fun `test keyed update preserves row and value`() {
        val rows = SettingsRows()
        val value = JButton("A")
        val row = rows.row("one", SettingsRow("One", "Before", value))

        val updated = rows.update("one", "Updated", "After", value)

        assertSame(row, updated)
        assertSame(value, components(row).first { it === value })
        assertTrue(text(row).contains("Updated"))
        assertTrue(text(row).contains("After"))
    }

    fun `test removing keyed row removes only that row`() {
        val rows = SettingsRows()
        val one = rows.row("one", SettingsRow("One", value = JButton("A")))
        val two = rows.row("two", SettingsRow("Two", value = JButton("B")))

        assertSame(one, rows.remove("one"))

        assertEquals(1, rows.componentCount)
        assertSame(two, rows.getComponent(0))
    }

    fun `test retain keeps requested keyed rows`() {
        val rows = SettingsRows()
        val one = rows.row("one", SettingsRow("One", value = JButton("A")))
        rows.row("two", SettingsRow("Two", value = JButton("B")))

        rows.retain(setOf("one"))

        assertEquals(1, rows.componentCount)
        assertSame(one, rows.getComponent(0))
    }

    fun `test top banner renders login action`() {
        val top = SettingsTop()

        top.showNotLoggedIn {}

        assertTrue(text(top).contains("Sign in to Kilo Code"))
        assertTrue(top.isVisible)
    }

    fun `test settings panel keeps banner in scroll content and progress in overlay`() {
        val panel = SettingsPanel()

        panel.top.showNotLoggedIn {}
        panel.showProgress("Loading models...")

        assertTrue(text(panel.content).contains("Sign in to Kilo Code"))
        assertFalse(text(panel.overlay).contains("Sign in to Kilo Code"))
        assertTrue(panel.overlay.components.any { it === panel.progress })
        assertTrue(text(panel.progress).contains("Loading models..."))
    }

    fun `test settings progress overlay is centered near top`() {
        val panel = SettingsPanel().apply { setSize(400, 300) }

        panel.showProgress("Loading")
        panel.doLayout()

        val size = panel.progress.preferredSize
        assertEquals(
            Rectangle((400 - size.width) / 2, UiStyle.Gap.pad(), size.width, size.height),
            panel.progress.bounds,
        )
        assertTrue(panel.overlay.contains(panel.progress.x + 1, panel.progress.y + 1))
        assertFalse(panel.overlay.contains(1, 1))
    }

    fun `test settings progress overlay retains label across updates`() {
        val panel = SettingsPanel()

        panel.showProgress("Loading")
        val label = components(panel.progress).filterIsInstance<JBLabel>().single { it.text == "Loading" }

        panel.showProgress("Saving")

        assertSame(label, components(panel.progress).filterIsInstance<JBLabel>().single { it.text == "Saving" })

        panel.clearProgress()
        assertFalse(panel.progress.isVisible)
    }

    fun `test settings progress overlay uses information colors`() {
        val panel = SettingsPanel()
        val hint = HintUtil.getInformationHint()

        panel.showProgress("Loading")

        val label = components(panel.progress).filterIsInstance<JBLabel>().single { it.text == "Loading" }
        assertEquals(hint.textBackground, panel.progress.background)
        assertEquals(hint.textForeground, panel.progress.foreground)
        assertEquals(hint.textForeground, label.foreground)
    }

    private fun text(component: JComponent): String = components(component)
        .mapNotNull {
            when (it) {
                is JLabel -> it.text
                is AbstractButton -> it.text
                is JTextComponent -> it.text
                else -> null
            }
        }
        .joinToString("\n")

    private fun components(component: JComponent): List<java.awt.Component> {
        val out = mutableListOf<java.awt.Component>()
        fun visit(c: java.awt.Component) {
            out += c
            if (c is Container) c.components.forEach { visit(it) }
        }
        visit(component)
        return out
    }
}
