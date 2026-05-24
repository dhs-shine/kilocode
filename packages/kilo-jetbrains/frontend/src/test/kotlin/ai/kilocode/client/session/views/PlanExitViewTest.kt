package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.model.toolKind
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class PlanExitViewTest : BasePlatformTestCase() {
    fun `test completed plan exit renders ready text and path`() {
        val tool = tool(ToolExecState.COMPLETED).apply {
            metadata = mapOf("plan" to ".kilo/plans/x.md")
        }

        val view = PlanExitView(tool)

        assertTrue(view.labelText().contains("Plan is ready"))
        assertTrue(view.labelText().contains(".kilo/plans/x.md"))
    }

    fun `test view factory replaces running tool with plan exit view when completed`() {
        val running = tool(ToolExecState.RUNNING)
        val existing = ViewFactory.create(running)
        assertTrue(existing is ToolView)

        val done = tool(ToolExecState.COMPLETED).apply {
            metadata = mapOf("plan" to ".kilo/plans/x.md")
        }

        assertTrue(ViewFactory.shouldReplace(existing, done))
        assertTrue(ViewFactory.create(done) is PlanExitView)
    }

    private fun tool(state: ToolExecState) = Tool("prt_plan", "plan_exit", toolKind("plan_exit")).apply {
        this.state = state
        output = "Plan is ready at .kilo/plans/x.md. Ending planning turn."
    }
}
