package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.toolKind
import ai.kilocode.client.session.views.base.SecondarySessionPartView
import ai.kilocode.client.session.views.tool.GlobToolView
import ai.kilocode.client.session.views.tool.ReadToolView
import ai.kilocode.client.session.views.tool.ToolView
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class GlobToolViewTest : BasePlatformTestCase() {

    fun `test header renders title directory and pattern rows`() {
        val view = GlobToolView(tool().also {
            it.input = mapOf("path" to "/repo/src", "pattern" to "**/*.kt")
        })
        val base: Any = view

        assertTrue(base is SecondarySessionPartView)
        assertTrue(view.labelText().contains("Glob"))
        assertEquals(listOf("/repo/src", "pattern=**/*.kt"), view.targetTexts())
        assertTrue(view.targetVisible(1))
    }

    fun `test pattern row hides when pattern is absent`() {
        val view = GlobToolView(tool().also {
            it.input = mapOf("path" to "/repo/src")
        })

        assertEquals(listOf("/repo/src"), view.targetTexts())
        assertFalse(view.targetVisible(1))
    }

    fun `test completed glob starts collapsed and expands output`() {
        val view = GlobToolView(tool().also { it.output = "/repo/src/A.kt\n/repo/src/B.kt" })

        assertTrue(view.hasToggle())
        assertFalse(view.isExpanded())
        assertFalse(view.bodyVisible())
        assertEquals("/repo/src/A.kt\n/repo/src/B.kt", view.bodyText())

        view.toggle()

        assertTrue(view.isExpanded())
        assertTrue(view.bodyVisible())
        assertEquals("/repo/src/A.kt\n/repo/src/B.kt", view.bodyText())
    }

    fun `test glob body is lazy and reused`() {
        val view = GlobToolView(tool().also { it.output = "/repo/src/A.kt" })

        assertFalse(view.bodyCreated())
        view.toggle()
        val body = view.scrollComponent()
        assertNotNull(body)

        view.toggle()
        assertFalse(view.bodyVisible())
        view.toggle()

        assertSame(body, view.scrollComponent())
        assertTrue(view.bodyVisible())
    }

    fun `test collapsed update keeps glob body uncreated`() {
        val view = GlobToolView(tool().also { it.output = "/repo/src/A.kt" })

        view.update(tool().also { it.output = "/repo/src/B.kt" })

        assertFalse(view.bodyCreated())
        assertEquals("/repo/src/B.kt", view.bodyText())
    }

    fun `test view factory routes glob to glob tool view`() {
        assertTrue(ViewFactory.create(tool(), openFile = {}) is GlobToolView)
    }

    fun `test should replace when glob renderer changes`() {
        val glob = tool()
        val read = Tool("p1", "read", toolKind("read")).also { it.state = ToolExecState.COMPLETED }

        assertTrue(ViewFactory.shouldReplace(ReadToolView(read), glob))
        assertTrue(ViewFactory.shouldReplace(ToolView(read), glob))
        assertTrue(ViewFactory.shouldReplace(GlobToolView(glob), read))
        assertFalse(ViewFactory.shouldReplace(GlobToolView(glob), glob))
    }

    private fun tool() = Tool("p1", "glob", toolKind("glob")).also { it.state = ToolExecState.COMPLETED }
}
