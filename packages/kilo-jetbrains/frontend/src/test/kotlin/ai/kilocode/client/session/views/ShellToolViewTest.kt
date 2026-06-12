package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.toolKind
import ai.kilocode.client.session.ui.selection.SessionSelection
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.session.views.tool.ShellToolView
import ai.kilocode.client.session.views.tool.ToolView
import ai.kilocode.client.ui.UiStyle
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.ScrollPaneConstants

@Suppress("UnstableApiUsage")
class ShellToolViewTest : BasePlatformTestCase() {
    private val views = mutableListOf<ShellToolView>()

    override fun tearDown() {
        try {
            views.forEach(Disposer::dispose)
            views.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test command only shell renders markdown`() {
        val view = track(ShellToolView(tool().also { it.input = mapOf("command" to "pwd") }))

        assertTrue(view.hasToggle())
        assertFalse(view.bodyCreated())
        assertEquals("pwd", view.bodyText())
        view.toggle()

        assertEquals("### Command\n\n```shell\npwd\n```", view.markdown())
        assertEquals(listOf("pwd"), view.codeTexts())
    }

    fun `test output only shell renders markdown`() {
        val view = track(ShellToolView(tool().also { it.output = "done" }))

        assertEquals("done", view.outputText())
        view.toggle()

        assertEquals("### Output\n\n```shell\ndone\n```", view.markdown())
        assertEquals(listOf("done"), view.codeTexts())
    }

    fun `test command and output render sections in order`() {
        val view = track(ShellToolView(tool().also {
            it.input = mapOf("command" to "git status", "description" to "Check status")
            it.output = "clean"
        }))

        assertTrue(view.labelText().contains("Shell"))
        assertTrue(view.labelText().contains("Check status"))
        assertEquals("git status", view.commandText())
        assertEquals("clean", view.outputText())
        assertEquals("git status\n\nclean", view.bodyText())
        view.toggle()

        assertEquals(
            "### Command\n\n```shell\ngit status\n```\n\n### Output\n\n```shell\nclean\n```",
            view.markdown(),
        )
        assertEquals(listOf("git status", "clean"), view.codeTexts())
    }

    fun `test ansi escapes are stripped from output`() {
        val view = track(ShellToolView(tool().also { it.output = "\u001B[32mgreen\u001B[0m line" }))

        assertEquals("green line", view.outputText())
        view.toggle()

        assertEquals(listOf("green line"), view.codeTexts())
    }

    fun `test carriage return frames keep last non-empty value`() {
        val view = track(ShellToolView(tool().also {
            it.output = "progress 1\rprogress 2\rprogress done\nstdout line\n"
        }))

        assertEquals("progress done\nstdout line\n", view.outputText())
        view.toggle()

        assertEquals(listOf("progress done\nstdout line"), view.codeTexts())
    }

    fun `test output backticks use longer markdown fence`() {
        val view = track(ShellToolView(tool().also { it.output = "before\n```\nafter" }))

        view.toggle()

        assertTrue(view.markdown().contains("````shell\nbefore\n```\nafter\n````"))
        assertEquals(listOf("before\n```\nafter"), view.codeTexts())
    }

    fun `test error section uses shell error text`() {
        val view = track(ShellToolView(tool(ToolExecState.ERROR).also {
            it.input = mapOf("command" to "fail")
            it.error = "boom"
        }))

        assertEquals("boom", view.errorText())
        assertTrue(view.labelText().contains("Error"))
        view.toggle()

        assertEquals(
            "### Command\n\n```shell\nfail\n```\n\n### Error\n\n```shell\nboom\n```",
            view.markdown(),
        )
        assertEquals(listOf("fail", "boom"), view.codeTexts())
    }

    fun `test body is created lazily and reused`() {
        val view = track(ShellToolView(tool().also {
            it.input = mapOf("command" to "pwd")
            it.output = "/tmp"
        }))

        assertFalse(view.bodyCreated())
        view.toggle()
        val body = view.mdComponent()
        val cmd = view.codeEditors().first()
        val out = view.codeEditors().last()
        view.toggle()
        view.toggle()

        assertSame(body, view.mdComponent())
        assertSame(cmd, view.codeEditors().first())
        assertSame(out, view.codeEditors().last())
        assertTrue(view.bodyVisible())
    }

    fun `test collapsed update does not create body`() {
        val view = track(ShellToolView(tool(ToolExecState.RUNNING).also {
            it.input = mapOf("command" to "pwd")
            it.output = "/tmp"
        }))

        view.update(tool().also {
            it.input = mapOf("command" to "pwd")
            it.output = "/home"
        })

        assertFalse(view.bodyCreated())
        assertEquals("pwd\n\n/home", view.bodyText())
    }

    fun `test update after first expand changes existing markdown body`() {
        val view = track(ShellToolView(tool(ToolExecState.RUNNING).also {
            it.input = mapOf("command" to "pwd")
            it.output = "/tmp"
        }))

        view.toggle()
        view.toggle()
        val body = view.mdComponent()
        val cmd = view.codeEditors().first()
        val out = view.codeEditors().last()
        view.update(tool().also {
            it.input = mapOf("command" to "pwd")
            it.output = "/home"
        })

        assertTrue(view.bodyCreated())
        assertFalse(view.bodyVisible())
        assertSame(body, view.mdComponent())
        assertSame(cmd, view.codeEditors().first())
        assertSame(out, view.codeEditors().last())
        assertEquals(listOf("pwd", "/home"), view.codeTexts())
    }

    fun `test applyStyle updates fonts in place`() {
        val view = track(ShellToolView(tool().also { it.output = "done" }))
        val style = SessionEditorStyle.create(family = "Courier New", size = 25)
        view.toggle()
        val editor = view.codeEditors().single()

        view.applyStyle(style)

        assertSame(editor, view.codeEditors().single())
        assertEquals(style.editorFont.name, view.commandFont().name)
        assertEquals(style.editorSize, view.commandFont().size)
        assertEquals(style.transcriptFont.name, view.titleFont().name)
        assertTrue(view.titleFont().isBold)
        assertEquals(style.transcriptFont.name, view.subtitleFont().name)
        assertEquals(style.transcriptFont.size, view.subtitleFont().size)
        assertFalse(view.subtitleFont().isBold)
        assertEquals(UiStyle.Colors.weak().rgb, view.subtitleForeground().rgb)
        assertTrue(view.stateFont().size < style.editorSize)
    }

    fun `test selection registers shell markdown editors`() {
        val selection = SessionSelection()
        val view = track(ShellToolView(tool().also { it.input = mapOf("command" to "pwd") }, selection))
        view.toggle()
        val editor = view.codeEditors().single().getEditor(true)
        editor?.selectionModel?.setSelection(0, 3)

        assertEquals("pwd", selection.selectedText())
        Disposer.dispose(selection)
    }

    fun `test shell view uses editor backed markdown code blocks`() {
        val style = SessionEditorStyle.current()
        val view = track(ShellToolView(tool().also { it.output = "done" }))
        view.toggle()

        assertEquals(style.editorFont.name, view.commandFont().name)
        assertEquals(1, view.codeEditors().size)
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, view.horizontalPolicy())
        assertTrue(view.preferredSize.height > 0)
    }

    fun `test shell labels are inset and code blocks use bottom border only`() {
        val view = track(ShellToolView(tool().also {
            it.input = mapOf("command" to "pwd")
            it.output = "/tmp"
        }))
        view.toggle()
        val root = view.mdComponent()!!
        val ins = root.border?.getBorderInsets(root)
        val labels = root.components.filterIsInstance<JBHtmlPane>()
        val panes = root.components.filterIsInstance<JBScrollPane>()

        assertEquals(0, ins?.left ?: 0)
        assertEquals(2, labels.size)
        assertEquals(2, panes.size)
        labels.forEach {
            val label = it.border.getBorderInsets(it)
            assertEquals(JBUI.scale(SessionUiStyle.View.Layout.HORIZONTAL_PADDING), label.left)
            assertEquals(0, label.right)
        }
        panes.forEach {
            val pane = it.border.getBorderInsets(it)
            assertEquals(0, pane.top)
            assertEquals(SessionUiStyle.View.Code.BORDER_WIDTH, pane.bottom)
            assertEquals(0, pane.left)
            assertEquals(0, pane.right)
            assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, it.verticalScrollBarPolicy)
            assertEquals(root.background.rgb, it.background.rgb)
            assertEquals(root.background.rgb, it.viewport.background.rgb)
        }
    }

    fun `test shell code blocks are editor backed and capped to fifteen lines`() {
        val output = (1..30).joinToString("\n") { "line $it" }
        val view = track(ShellToolView(tool().also { it.output = output }))
        view.toggle()
        val root = view.mdComponent()!!
        val pane = root.components.filterIsInstance<JBScrollPane>().single()
        val editor = view.codeEditors().single()
        val nested = editor.getEditor(true)!!.scrollPane
        val chrome = pane.insets.top + pane.insets.bottom +
            pane.viewportBorder.getBorderInsets(pane).top + pane.viewportBorder.getBorderInsets(pane).bottom +
            pane.horizontalScrollBar.preferredSize.height

        assertEquals(output, editor.text)
        assertEquals(1, view.codeEditors().size)
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, pane.verticalScrollBarPolicy)
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, nested.verticalScrollBarPolicy)
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, nested.horizontalScrollBarPolicy)
        assertTrue(editor.preferredSize.height > pane.preferredSize.height - chrome)
        assertTrue(pane.preferredSize.height < editor.preferredSize.height + chrome)
    }

    fun `test view factory routes bash and replaces generic views`() {
        val bash = tool()
        val other = Tool("p1", "mystery", toolKind("mystery")).also { it.state = ToolExecState.COMPLETED }

        assertTrue(ViewFactory.create(bash, openFile = {}) is ShellToolView)
        assertTrue(ViewFactory.shouldReplace(ToolView(bash), bash))
        assertTrue(ViewFactory.shouldReplace(ShellToolView(bash), other))
        assertFalse(ViewFactory.shouldReplace(ShellToolView(bash), bash))
    }

    fun `test shell editors are disposed after churn`() {
        val base = EditorFactory.getInstance().allEditors.size

        repeat(40) { i ->
            val view = ShellToolView(tool().also {
                it.input = mapOf("command" to "log $i")
                it.output = (1..20).joinToString("\n") { line -> "line $i/$line" }
            })
            view.toggle()
            view.codeEditors().forEach { it.getEditor(true) }
            Disposer.dispose(view)
        }
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(base, EditorFactory.getInstance().allEditors.size)
    }

    private fun ShellToolView.codeTexts() = codeEditors().map { it.text }

    private fun tool(state: ToolExecState = ToolExecState.COMPLETED) = Tool("p1", "bash", toolKind("bash")).also {
        it.state = state
    }

    private fun track(view: ShellToolView): ShellToolView {
        views.add(view)
        return view
    }
}
