package ai.kilocode.client.settings.agents

import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.SettingsListItem
import ai.kilocode.client.settings.base.settingsListCellBounds
import ai.kilocode.client.testing.FakeAgentBehaviorRpcApi
import ai.kilocode.client.testing.FakeAppRpcApi
import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.McpConfigDto
import ai.kilocode.rpc.dto.McpStatusDto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextField

class McpSettingsUiTest : BasePlatformTestCase() {
    private var scope: CoroutineScope? = null
    private var ui: McpSettingsUi? = null
    private lateinit var app: KiloAppService
    private lateinit var appRpc: FakeAppRpcApi
    private lateinit var agentRpc: FakeAgentBehaviorRpcApi

    override fun tearDown() {
        try {
            ui?.let { panel -> edt { panel.dispose(); true } }
            ui = null
            scope?.cancel()
            scope = null
        } finally {
            super.tearDown()
        }
    }

    fun `test loads configured mcp servers with runtime status`() {
        val panel = panel()

        flushUntil { rows(panel).size == 3 }

        edt {
            val rows = rows(panel)
            assertEquals(listOf("filesystem", "github", "runtime"), rows.map { it.key })
            assertEquals(listOf("connected", "stdio"), rows.single { it.key == "filesystem" }.badges.map { it.text })
            assertEquals("bun mcp-files", rows.single { it.key == "filesystem" }.description)
            assertEquals(listOf("needs auth", "remote"), rows.single { it.key == "github" }.badges.map { it.text })
            assertEquals("https://mcp.github.test", rows.single { it.key == "github" }.description)
            assertEquals(listOf("failed"), rows.single { it.key == "runtime" }.badges.map { it.text })
            assertEquals("crashed", rows.single { it.key == "runtime" }.description)
            assertTrue(rows.single { it.key == "github" }.cells.any { it.id == "auth" })
            assertTrue(rows.single { it.key == "github" }.cells.any { it.id == "remove" })
            assertFalse(rows.single { it.key == "runtime" }.cells.any { it.id == "remove" })
            assertEquals(listOf(DIR), agentRpc.mcpCalls)
            true
        }
    }

    fun `test refresh reloads latest mcp status`() {
        val panel = panel()
        flushUntil { rows(panel).size == 3 }

        agentRpc.mcps = listOf(McpStatusDto("filesystem", "disabled"))
        edt { panel.reload(); true }

        flushUntil { rows(panel).single { it.key == "filesystem" }.badges.first().text == "disabled" }
        assertEquals(listOf(DIR, DIR), agentRpc.mcpCalls)
    }

    fun `test connect action updates runtime status and keeps selection`() {
        val panel = panel()
        flushUntil { rows(panel).size == 3 }
        agentRpc.afterMcpConnect = { _, name ->
            agentRpc.mcps = agentRpc.mcps.filterNot { it.name == name } + McpStatusDto(name, "connected")
        }

        click(panel, "github", "connect")

        flushUntil { rows(panel).single { it.key == "github" }.badges.first().text == "connected" }
        assertEquals(listOf("github"), agentRpc.mcpConnects)
        assertEquals("github", edt { list(panel).selectedValue?.key })
    }

    fun `test remove action writes mcp config patch and reloads`() {
        val panel = panel()
        flushUntil { rows(panel).size == 3 }
        agentRpc.mcps = agentRpc.mcps.filterNot { it.name == "filesystem" }

        click(panel, "filesystem", "remove")

        flushUntil { rows(panel).none { it.key == "filesystem" } }
        val patch = appRpc.configPatches.single().mcp.orEmpty()
        assertTrue(patch.containsKey("filesystem"))
        assertNull(patch["filesystem"])
    }

    fun `test failed mcp action shows settings error`() {
        val panel = panel()
        flushUntil { rows(panel).size == 3 }
        agentRpc.mcpConnectResult = false

        click(panel, "runtime", "connect")

        val message = KiloBundle.message("settings.agentBehavior.mcp.action.failed")
        flushUntil { text(panel).contains(message) }
        assertTrue(edt { rows(panel).any { it.key == "runtime" } })
    }

    fun `test missing directory still shows configured mcp servers`() {
        install()
        val panel = edt { McpSettingsUi(scope!!, "") }
        ui = panel
        edt { panel.reload(); true }

        flushUntil { rows(panel).size == 2 }

        edt {
            assertEquals(listOf("filesystem", "github"), rows(panel).map { it.key })
            assertTrue(agentRpc.mcpCalls.isEmpty())
            true
        }
    }

    fun `test configurable lifecycle triggers initial list load`() {
        install()
        val cfg = TestConfigurable()

        val shell = edt { cfg.createComponent() }

        flushUntil { components(shell).filterIsInstance<McpSettingsUi>().singleOrNull()?.let { rows(it).size == 3 } == true }
        ui = components(shell).filterIsInstance<McpSettingsUi>().single()
        assertEquals(listOf(DIR), agentRpc.mcpCalls)
        edt { cfg.disposeUIResources(); true }
    }

    private fun panel(): McpSettingsUi {
        install()
        val panel = edt { McpSettingsUi(scope!!, DIR) }
        ui = panel
        edt { panel.reload(); true }
        return panel
    }

    private fun install() {
        val cs = CoroutineScope(SupervisorJob())
        scope = cs
        appRpc = FakeAppRpcApi()
        agentRpc = FakeAgentBehaviorRpcApi().apply {
            mcps = listOf(
                McpStatusDto("filesystem", "connected"),
                McpStatusDto("github", "needs_auth"),
                McpStatusDto("runtime", "failed", "crashed"),
            )
        }
        app = KiloAppService(cs, appRpc)
        val ready = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(mcp = mapOf(
                "filesystem" to McpConfigDto(type = "stdio", command = listOf("bun", "mcp-files")),
                "github" to McpConfigDto(type = "remote", url = "https://mcp.github.test"),
            )),
        )
        app._state.value = ready
        appRpc.state.value = ready
        ApplicationManager.getApplication().replaceService(KiloAppService::class.java, app, testRootDisposable)
        ApplicationManager.getApplication().replaceService(KiloAgentBehaviorService::class.java, KiloAgentBehaviorService(cs, agentRpc), testRootDisposable)
    }

    private fun click(panel: McpSettingsUi, key: String, id: String) {
        edt {
            val list = list(panel)
            list.size = Dimension(460, 260)
            list.doLayout()
            val idx = rows(panel).indexOfFirst { it.key == key }
            list.selectedIndex = idx
            val row = rows(panel)[idx]
            val bounds = list.getCellBounds(idx, idx)
            val area = settingsListCellBounds(list, bounds, row, selected = true).getValue(id)
            click(list, center(area))
            true
        }
    }

    private fun rows(panel: McpSettingsUi): List<SettingsListItem> {
        val model = list(panel).model
        return (0 until model.size).map { model.getElementAt(it) }
    }

    private fun list(panel: McpSettingsUi) = components(panel).filterIsInstance<JBList<SettingsListItem>>().single()

    private fun components(root: java.awt.Component): List<java.awt.Component> {
        val out = mutableListOf<java.awt.Component>()
        fun visit(item: java.awt.Component) {
            out += item
            if (item is Container) item.components.forEach { visit(it) }
        }
        visit(root)
        return out
    }

    private fun text(root: Container): String {
        val out = mutableListOf<String>()
        for (comp in components(root)) {
            if (!comp.isVisible) continue
            when (comp) {
                is JButton -> comp.text?.let { out.add(it) }
                is JBLabel -> comp.text?.let { out.add(it) }
                is JTextField -> comp.text?.let { out.add(it) }
            }
        }
        return out.joinToString("\n")
    }

    private fun center(rect: java.awt.Rectangle) = Point(rect.x + rect.width / 2, rect.y + rect.height / 2)

    private fun click(list: JBList<SettingsListItem>, point: Point) {
        list.dispatchEvent(mouse(list, MouseEvent.MOUSE_PRESSED, point))
        list.dispatchEvent(mouse(list, MouseEvent.MOUSE_RELEASED, point))
    }

    private fun mouse(list: JBList<SettingsListItem>, id: Int, point: Point) = MouseEvent(
        list,
        id,
        System.currentTimeMillis(),
        if (id == MouseEvent.MOUSE_PRESSED) InputEvent.BUTTON1_DOWN_MASK else 0,
        point.x,
        point.y,
        1,
        false,
        MouseEvent.BUTTON1,
    )

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun flushUntil(done: () -> Boolean) = runBlocking {
        repeat(30) {
            delay(100)
            edt { UIUtil.dispatchAllInvocationEvents(); true }
            if (done()) return@runBlocking
        }
        edt { UIUtil.dispatchAllInvocationEvents(); true }
        assertTrue(done())
    }

    private companion object {
        const val DIR = "/test"
    }

    private class TestConfigurable : AgentBehaviorConfigurableBase<JComponent>() {
        override fun getId() = "test.mcp"
        override fun getDisplayName() = "test"
        override fun create(cs: CoroutineScope, dir: String): JComponent = McpSettingsUi(cs, DIR)
    }
}
