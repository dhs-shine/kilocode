package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.toolKind
import ai.kilocode.client.session.views.tool.ToolView
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

@Suppress("UnstableApiUsage")
class ToolBodyStressTest : BasePlatformTestCase() {

    fun `test expanded tool body editors are disposed after churn`() {
        val base = EditorFactory.getInstance().allEditors.size

        repeat(60) { i ->
            val view = ToolView(tool(i))
            view.toggle()
            view.bodyEditor()?.getEditor(true)
            Disposer.dispose(view)
        }
        drainEdt()

        assertEquals(base, EditorFactory.getInstance().allEditors.size)
    }

    private fun tool(index: Int) = Tool("p$index", "bash", toolKind("bash")).also {
        it.state = ToolExecState.COMPLETED
        it.input = mapOf("command" to "log $index")
        it.output = (1..20).joinToString("\n") { line -> "line $index/$line" }
    }

    private fun drainEdt() {
        UIUtil.dispatchAllInvocationEvents()
    }
}
