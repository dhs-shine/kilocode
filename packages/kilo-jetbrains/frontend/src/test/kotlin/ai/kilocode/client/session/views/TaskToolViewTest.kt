package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.toolKind
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.session.views.base.SecondarySessionPartView
import ai.kilocode.client.session.views.tool.TaskToolView
import ai.kilocode.client.ui.UiStyle
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.ScrollPaneConstants

@Suppress("UnstableApiUsage")
class TaskToolViewTest : BasePlatformTestCase() {
    private val views = mutableListOf<TaskToolView>()

    override fun tearDown() {
        try {
            views.forEach(Disposer::dispose)
            views.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test task tool uses secondary chrome`() {
        val base: Any = view(task())

        assertTrue(base is SecondarySessionPartView)
    }

    fun `test task header shows agent description and count`() {
        val view = view(task(children = listOf(child("c1", "read"), child("c2", "grep"))))

        assertTrue(view.labelText().contains("Explore Agent"))
        assertTrue(view.labelText().contains("Find files (2)"))
        assertEquals(2, view.rowCount())
        assertTrue(view.bodyVisible())
    }

    fun `test update adds child row without replacing existing rows`() {
        val view = view(task(children = listOf(child("c1", "read"))))
        val before = view.rowLabels().first()

        view.update(task(children = listOf(child("c1", "read"), child("c2", "grep"))))

        assertEquals(2, view.rowCount())
        assertEquals(before, view.rowLabels().first())
        assertTrue(view.rowLabels().any { it.contains("Grep") })
    }

    fun `test removing child rows collapses body`() {
        val view = view(task(children = listOf(child("c1", "read"))))

        view.update(task(children = emptyList()))

        assertEquals(0, view.rowCount())
        assertFalse(view.bodyVisible())
    }

    fun `test body is lazy until child tools arrive`() {
        val view = view(task(children = emptyList()))

        assertFalse(view.bodyCreated())
        view.update(task(children = listOf(child("c1", "read"))))

        assertTrue(view.bodyCreated())
        assertTrue(view.bodyVisible())
    }

    fun `test collapsed task body stays collapsed on child update`() {
        val view = view(task(children = listOf(child("c1", "read"))))

        view.collapse()
        view.update(task(children = listOf(child("c1", "grep"))))

        assertFalse(view.bodyVisible())
        assertTrue(view.rowLabels().single().contains("Grep"))
        assertTrue(view.rowLabels().single().contains("pattern=query"))
    }

    fun `test expanded task body is capped to ten rows`() {
        val view = view(task(children = children(20)))
        val taller = view(task(children = children(80)))

        assertEquals(10, view.bodyMaxRows())
        assertTrue(view.preferredSize.height > 0)
        assertEquals(view.preferredSize.height, taller.preferredSize.height)
    }

    fun `test task body uses nested vertical scroll`() {
        val view = view(task(children = children(20)))

        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, view.horizontalPolicy())
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, view.verticalPolicy())
    }

    fun `test child tool titles use target color`() {
        val view = view(task(children = listOf(child("c1", "read"), child("c2", "grep", ToolExecState.ERROR))))

        assertColor(UiStyle.Colors.weak(), view.rowTitleColor("c1"))
        assertColor(UiStyle.Colors.errorLabelForeground(), view.rowTitleColor("c2"))
    }

    fun `test task body is indented beyond header padding`() {
        val view = view(task(children = listOf(child("c1", "read"))))

        assertTrue(view.bodyInsets().left > JBUI.scale(SessionUiStyle.View.Layout.HORIZONTAL_PADDING))
        assertEquals(UiStyle.Gap.sm(), view.bodyInsets().top)
        assertEquals(UiStyle.Gap.sm(), view.bodyInsets().bottom)
    }

    fun `test appended child tools scroll nested body to bottom`() {
        val view = view(task(children = children(40)))
        view.setSize(300, view.preferredSize.height)
        view.doLayout()
        UIUtil.dispatchAllInvocationEvents()
        view.setBodyScrollValue(view.bodyScrollBottom() - 1)

        view.update(task(children = children(70)))
        UIUtil.dispatchAllInvocationEvents()
        UIUtil.dispatchAllInvocationEvents()
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(view.bodyScrollBottom(), view.bodyScrollValue())
    }

    fun `test appended child tools do not yank nested body above tail`() {
        val view = view(task(children = children(40)))
        view.setSize(300, view.preferredSize.height)
        view.doLayout()
        UIUtil.dispatchAllInvocationEvents()
        view.setBodyScrollValue(0)

        view.update(task(children = children(70)))
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(0, view.bodyScrollValue())
    }

    private fun view(tool: Tool): TaskToolView = TaskToolView(tool).also { views.add(it) }

    private fun task(children: List<Tool> = emptyList()) = Tool("part_task", "task", toolKind("task")).also {
        it.state = ToolExecState.COMPLETED
        it.input = mapOf("subagent_type" to "explore", "description" to "Find files")
        it.metadata = mapOf("sessionId" to "ses_child")
        it.childSessionId = "ses_child"
        it.childTools = children
    }

    private fun child(id: String, name: String, state: ToolExecState = ToolExecState.COMPLETED) = Tool(id, name, toolKind(name)).also {
        it.state = state
        it.input = mapOf("filePath" to "src/Main.kt", "pattern" to "query")
    }

    private fun children(count: Int) = (1..count).map { child("c$it", "read") }

    private fun assertColor(expected: Color, actual: Color?) {
        assertNotNull(actual)
        assertEquals(expected.rgb, actual!!.rgb)
    }
}
