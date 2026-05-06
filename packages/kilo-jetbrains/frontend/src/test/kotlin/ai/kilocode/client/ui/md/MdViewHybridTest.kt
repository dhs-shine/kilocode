package ai.kilocode.client.ui.md

import ai.kilocode.client.session.ui.SessionStyle
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

@Suppress("UnstableApiUsage")
class MdViewHybridTest : BasePlatformTestCase() {
    private lateinit var view: MdView

    override fun setUp() {
        super.setUp()
        view = MdViewFactory.hybrid()
    }

    fun `test set stores source`() {
        view.set("hello **world**")
        assertEquals("hello **world**", view.markdown())
    }

    fun `test append renders accumulated source`() {
        view.append("hello ")
        view.append("**world**")
        assertEquals("hello **world**", view.markdown())
        assertTrue(view.html().contains("<strong>"))
    }

    fun `test fenced code block creates horizontal scroll pane`() {
        view.set("```kotlin\nval value = 1\n```")
        val pane = scrolls().single()

        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, pane.horizontalScrollBarPolicy)
    }

    fun `test clear resets source and components`() {
        view.set("```\ncode\n```")
        view.clear()

        assertEquals("", view.markdown())
        assertTrue(scrolls().isEmpty())
    }

    fun `test applyStyle updates current and future blocks`() {
        val style = SessionStyle.create(family = "Courier New", size = 21)

        view.applyStyle(style)
        view.set("hello")

        assertEquals("Courier New", view.font.name)
        assertEquals(21, view.font.size)
        assertTrue(view.overrideSheet().contains("Courier New"))
        assertTrue(view.overrideSheet().contains("21pt"))
    }

    fun `test resetStyles keeps content rendered`() {
        view.set("hello **world**")
        view.font = view.font.deriveFont(25f)

        view.resetStyles()

        assertEquals("hello **world**", view.markdown())
        assertTrue(view.html().contains("<strong>"))
    }

    fun `test link listener receives simulated link`() {
        val received = mutableListOf<MdView.LinkEvent>()
        view.addLinkListener { received.add(it) }

        view.simulateLink("https://example.com")

        assertEquals("https://example.com", received.single().href)
    }

    private fun scrolls(): List<JBScrollPane> = (view.component as JPanel).components.filterIsInstance<JBScrollPane>()
}
