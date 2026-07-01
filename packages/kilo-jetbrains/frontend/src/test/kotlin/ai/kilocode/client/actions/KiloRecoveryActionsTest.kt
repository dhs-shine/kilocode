package ai.kilocode.client.actions

import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.session.SessionManager
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import ai.kilocode.rpc.dto.ConfigTargetDto
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@Suppress("UnstableApiUsage")
class KiloRecoveryActionsTest : BasePlatformTestCase() {
    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeWorkspaceRpcApi

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        rpc = FakeWorkspaceRpcApi()
        ApplicationManager.getApplication().replaceService(
            KiloWorkspaceService::class.java,
            KiloWorkspaceService(scope, rpc),
            testRootDisposable,
        )
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test restart action stays enabled for all app states`() {
        val action = RestartKiloAction()
        val event = event(action)

        update(action, event)

        assertTrue("Restart should force-enable recovery action", event.presentation.isEnabled)
    }

    fun `test reinstall action stays enabled for all app states`() {
        val action = ReinstallKiloAction()
        val event = event(action)

        update(action, event)

        assertTrue("Reinstall should force-enable recovery action", event.presentation.isEnabled)
    }

    fun `test restart action adds cli suffix in connection retry popup`() {
        val action = RestartKiloAction()
        val event = event(action, place = KiloActionPlaces.connectionRetryPopup())

        update(action, event)

        assertEquals("Restart CLI", event.presentation.text)
    }

    fun `test reinstall action adds cli suffix in connection retry popup`() {
        val action = ReinstallKiloAction()
        val event = event(action, place = KiloActionPlaces.connectionRetryPopup())

        update(action, event)

        assertEquals("Reinstall CLI", event.presentation.text)
    }

    fun `test cli group has visible menu text`() {
        val xml = requireNotNull(javaClass.classLoader.getResourceAsStream("kilo.jetbrains.frontend.xml"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(xml.contains("<group id=\"Kilo.CliGroup\" text=\"CLI\" popup=\"true\">"))
        assertTrue(xml.contains("<reference ref=\"Kilo.Restart\"/>"))
        assertTrue(xml.contains("<reference ref=\"Kilo.Reinstall\"/>"))
        assertTrue(xml.contains("<group id=\"Kilo.OpenConfigGroup\" text=\"Config Files\" popup=\"true\">"))
        assertTrue(xml.contains("<reference ref=\"Kilo.OpenConfigGroup\"/>"))
        assertFalse(xml.contains("<action id=\"Kilo.ShowProfile\""))
        assertFalse(xml.contains("<reference ref=\"Kilo.ShowProfile\"/>"))
    }

    fun `test local config action says open when target exists`() {
        rpc.localConfigPath = "/test/.kilo/kilo.jsonc"
        rpc.localConfigDisplayPath = "~/.kilo/kilo.jsonc"
        rpc.localConfigExists = true
        service().localConfig["/test"] = ConfigTargetDto("/test/.kilo/kilo.jsonc", "~/.kilo/kilo.jsonc", true)
        val action = OpenLocalConfigAction()
        val event = event(action, workspace = workspace("/test"))

        update(action, event)

        assertTrue(event.presentation.isEnabled)
        assertEquals("Open: local ~/.kilo/kilo.jsonc", event.presentation.text)
        assertEquals(0, rpc.localConfigPathCalls)
    }

    fun `test local config action says create when target is missing`() {
        rpc.localConfigPath = "/test/.kilo/kilo.jsonc"
        rpc.localConfigDisplayPath = "~/.kilo/kilo.jsonc"
        rpc.localConfigExists = false
        service().localConfig["/test"] = ConfigTargetDto("/test/.kilo/kilo.jsonc", "~/.kilo/kilo.jsonc", false)
        val action = OpenLocalConfigAction()
        val event = event(action, workspace = workspace("/test"))

        update(action, event)

        assertTrue(event.presentation.isEnabled)
        assertEquals("Create: local ~/.kilo/kilo.jsonc", event.presentation.text)
        assertEquals(0, rpc.localConfigPathCalls)
    }

    fun `test local config action refreshes missing target in background`() {
        rpc.localConfigPath = "/test/.kilo/kilo.jsonc"
        rpc.localConfigDisplayPath = "/test/.kilo/kilo.jsonc"
        rpc.localConfigExists = true
        val action = OpenLocalConfigAction()
        val event = event(action, workspace = workspace("/test"))

        update(action, event)

        assertTrue(event.presentation.isEnabled)
        assertEquals("Open: local ...", event.presentation.text)
        waitFor { rpc.localConfigPathCalls == 1 && service().localConfig["/test"] != null }

        val next = event(action, workspace = workspace("/test"))
        update(action, next)

        assertEquals("Open: local /test/.kilo/kilo.jsonc", next.presentation.text)
        assertEquals(1, rpc.localConfigPathCalls)
    }

    fun `test local config action dedupes in flight refresh`() {
        val gate = CompletableDeferred<Unit>()
        rpc.beforeLocalConfigTarget = { gate.await() }
        val action = OpenLocalConfigAction()

        update(action, event(action, workspace = workspace("/test")))
        waitFor { rpc.localConfigPathCalls == 1 }
        update(action, event(action, workspace = workspace("/test")))

        assertEquals(1, rpc.localConfigPathCalls)

        gate.complete(Unit)
        waitFor { service().localConfig["/test"] != null }
    }

    fun `test global config action says open when target exists`() {
        rpc.globalConfigPath = "/config/kilo.jsonc"
        rpc.globalConfigDisplayPath = "~/.config/kilo/kilo.jsonc"
        rpc.globalConfigExists = true
        cacheGlobal(ConfigTargetDto("/config/kilo.jsonc", "~/.config/kilo/kilo.jsonc", true))
        val action = OpenGlobalConfigAction()
        val event = event(action)

        update(action, event)

        assertEquals("Open: global ~/.config/kilo/kilo.jsonc", event.presentation.text)
        assertEquals(0, rpc.globalConfigPathCalls)
    }

    fun `test global config action says create when target is missing`() {
        rpc.globalConfigPath = "/config/kilo.jsonc"
        rpc.globalConfigDisplayPath = "~/.config/kilo/kilo.jsonc"
        rpc.globalConfigExists = false
        cacheGlobal(ConfigTargetDto("/config/kilo.jsonc", "~/.config/kilo/kilo.jsonc", false))
        val action = OpenGlobalConfigAction()
        val event = event(action)

        update(action, event)

        assertEquals("Create: global ~/.config/kilo/kilo.jsonc", event.presentation.text)
        assertEquals(0, rpc.globalConfigPathCalls)
    }

    fun `test global config action refreshes missing target in background`() {
        rpc.globalConfigPath = "/config/kilo.jsonc"
        rpc.globalConfigDisplayPath = "/config/kilo.jsonc"
        rpc.globalConfigExists = true
        val action = OpenGlobalConfigAction()
        val event = event(action)

        update(action, event)

        assertEquals("Open: global ...", event.presentation.text)
        waitFor { rpc.globalConfigPathCalls == 1 && service().globalConfig != null }

        val next = event(action)
        update(action, next)

        assertEquals("Open: global /config/kilo.jsonc", next.presentation.text)
        assertEquals(1, rpc.globalConfigPathCalls)
    }

    fun `test global config action dedupes in flight refresh`() {
        val gate = CompletableDeferred<Unit>()
        rpc.beforeGlobalConfigTarget = { gate.await() }
        val action = OpenGlobalConfigAction()

        update(action, event(action))
        waitFor { rpc.globalConfigPathCalls == 1 }
        update(action, event(action))

        assertEquals(1, rpc.globalConfigPathCalls)

        gate.complete(Unit)
        waitFor { service().globalConfig != null }
    }

    fun `test local config action disables without directory`() {
        val action = OpenLocalConfigAction()
        val event = event(action)

        update(action, event)

        assertFalse(event.presentation.isEnabled)
        assertEquals(0, rpc.localConfigPathCalls)
    }

    fun `test settings popup group updates recursively in background`() {
        val group = DefaultActionGroup()
        val wrapped = KiloSettingsAction.popupGroup(group)

        assertEquals(ActionUpdateThread.BGT, wrapped.actionUpdateThread)
    }

    fun `test settings action prewarms config targets`() {
        val action = KiloSettingsAction()

        KiloSettingsAction.refreshConfigTargets(event(action, workspace = workspace("/test")), service())

        waitFor { rpc.localConfigPathCalls == 1 && rpc.globalConfigPathCalls == 1 }
    }

    fun `test workspace creation prewarms config targets`() {
        service().workspace("/test")

        waitFor { rpc.localConfigPathCalls == 1 && rpc.globalConfigPathCalls == 1 }

        assertEquals(1, rpc.localConfigPathCalls)
        assertEquals(1, rpc.globalConfigPathCalls)
    }

    private fun event(action: AnAction, workspace: Workspace? = null, place: String = ""): AnActionEvent {
        val presentation = Presentation().apply { copyFrom(action.templatePresentation) }
        presentation.isEnabled = false
        return AnActionEvent.createFromDataContext(place, presentation, context(workspace))
    }

    private fun update(action: AnAction, event: AnActionEvent) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ActionUtil.updateAction(action, event)
        }.get()
    }

    private fun waitFor(done: () -> Boolean) = runBlocking {
        withTimeout(5_000) {
            while (!done()) {
                delay(25)
                ApplicationManager.getApplication().invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
            }
        }
        ApplicationManager.getApplication().invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
    }

    private fun service(): KiloWorkspaceService = ApplicationManager.getApplication().getService(KiloWorkspaceService::class.java)

    private fun cacheGlobal(target: ConfigTargetDto) {
        val field = KiloWorkspaceService::class.java.getDeclaredField("globalConfig")
        field.isAccessible = true
        field.set(service(), target)
    }

    private fun context(workspace: Workspace?): DataContext {
        return DataContext { id ->
            when (id) {
                SessionManager.WORKSPACE_KEY.name -> workspace
                CommonDataKeys.PROJECT.name -> project.takeIf { workspace != null }
                else -> null
            }
        }
    }

    private fun workspace(dir: String): Workspace {
        return Workspace(
            dir,
            MutableStateFlow(KiloWorkspaceStateDto(KiloWorkspaceStatusDto.READY)),
            reload = {},
        )
    }
}
