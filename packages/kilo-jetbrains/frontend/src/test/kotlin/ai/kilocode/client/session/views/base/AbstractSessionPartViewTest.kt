package ai.kilocode.client.session.views.base

import ai.kilocode.client.session.model.Content
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JLabel

@Suppress("UnstableApiUsage")
class AbstractSessionPartViewTest : BasePlatformTestCase() {

    fun `test collapsed by default`() {
        val content = JLabel("body")
        val view = TestView(content = content)

        assertFalse(view.isExpanded())
        assertNull(content.parent)
    }

    fun `test expanded when requested`() {
        val content = JLabel("body")
        val view = TestView(content = content, expanded = true)

        assertTrue(view.isExpanded())
        assertSame(view, content.parent)
    }

    fun `test toggle reuses content component`() {
        val content = JLabel("body")
        val view = TestView(content = content)

        view.syncExpandable(true)
        view.toggle()
        assertSame(view, content.parent)
        view.toggle()
        assertNull(content.parent)
        view.toggle()
        assertSame(view, content.parent)
    }

    fun `test non expandable hides content`() {
        val content = JLabel("body")
        val view = TestView(content = content, expanded = true)

        view.syncExpandable(false)

        assertFalse(view.isExpanded())
        assertNull(content.parent)
    }

    private class TestView(content: JLabel, expanded: Boolean = false) :
        PrimarySessionPartView(JLabel("header"), content, expanded) {

        override val contentId = "test"
        override fun update(content: Content) {}
    }
}
