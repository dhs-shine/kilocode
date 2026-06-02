package ai.kilocode.client.settings.models

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.testing.FakeAppRpcApi
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ModelsWorkspaceDto
import ai.kilocode.rpc.dto.ProfileDto
import ai.kilocode.rpc.dto.ProviderDto
import ai.kilocode.rpc.dto.ProvidersDto
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InlineBanner
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.text.JTextComponent

class ModelsSettingsUiTest : BasePlatformTestCase() {
    private lateinit var appScope: CoroutineScope
    private lateinit var uiScope: CoroutineScope
    private lateinit var rpc: FakeAppRpcApi
    private lateinit var workspaceRpc: FakeWorkspaceRpcApi
    private lateinit var app: KiloAppService
    private lateinit var workspaces: KiloWorkspaceService
    private var ui: ModelsSettingsUi? = null

    override fun setUp() {
        super.setUp()
        appScope = CoroutineScope(SupervisorJob())
        uiScope = CoroutineScope(SupervisorJob())
        rpc = FakeAppRpcApi()
        workspaceRpc = FakeWorkspaceRpcApi()
        app = KiloAppService(appScope, rpc)
        workspaces = KiloWorkspaceService(appScope, workspaceRpc)
        val state = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(model = "kilo/old"),
            profile = ProfileDto(email = "alice@test.com"),
        )
        rpc.state.value = state
        app._state.value = state
        workspaceRpc.models = ModelsWorkspaceDto(providers = providers())
        edt { ui = ModelsSettingsUi(uiScope, app, workspaces, directory = "/test") }
        flushUntil { text(requireUi()).contains("Old") }
    }

    override fun tearDown() {
        try {
            val panel = ui
            if (panel != null) edt { panel.dispose() }
            ui = null
            uiScope.cancel()
            appScope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test failed apply stays visible while panel open`() {
        val panel = requireUi()
        rpc.configUpdateError = RuntimeException("save failed")

        edt {
            select(panel, "new")
            panel.applyDraft()
        }

        flushUntil { text(panel).contains("Failed to save model settings") }
        edt {
            assertTrue(text(panel.progress).contains("Failed to save model settings"))
            assertTrue(panel.modified())
        }
    }

    fun `test edit clears save error`() {
        val panel = requireUi()
        rpc.configUpdateError = RuntimeException("save failed")
        edt {
            select(panel, "new")
            panel.applyDraft()
        }
        flushUntil { text(panel).contains("Failed to save model settings") }

        edt { select(panel, "old") }

        flushUntil { !text(panel).contains("Failed to save model settings") }
    }

    fun `test failed save after dispose shows notification`() {
        val notes = mutableListOf<Notification>()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notes.add(notification)
                }
            },
        )
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notes.add(notification)
                }
            },
        )
        val panel = requireUi()
        rpc.configUpdateGate = CompletableDeferred()
        rpc.configUpdateError = RuntimeException("save failed")

        edt {
            select(panel, "new")
            panel.applyDraft()
            panel.dispose()
            ui = null
        }
        rpc.configUpdateGate?.complete(Unit)

        flushUntil {
            notes.any { it.groupId == "Kilo Code" && it.type == NotificationType.ERROR }
        }
        assertEquals(1, rpc.configUpdateAttempts)
        assertTrue(notes.any { it.title == "Failed to save model settings" })
    }

    fun `test logged out save keeps login banner stable`() {
        edt {
            requireUi().dispose()
            ui = null
        }
        uiScope = CoroutineScope(SupervisorJob())
        val state = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(model = "kilo/old"),
            profile = null,
        )
        rpc.state.value = state
        app._state.value = state
        workspaceRpc.models = ModelsWorkspaceDto(providers = providers())
        edt { ui = ModelsSettingsUi(uiScope, app, workspaces, directory = "/test") }
        val panel = requireUi()
        flushUntil { text(panel).contains("Old") && text(panel).contains("Sign in to Kilo Code") }
        val banner = edt { components(panel.top).filterIsInstance<InlineBanner>().single() }
        rpc.configUpdateGate = CompletableDeferred()

        edt {
            select(panel, "new")
            panel.applyDraft()
            assertTrue(text(panel).contains("Sign in to Kilo Code"))
            assertSame(banner, components(panel.top).filterIsInstance<InlineBanner>().single())
        }

        rpc.configUpdateGate?.complete(Unit)
        flushUntil { rpc.configPatches.isNotEmpty() }
        edt {
            assertTrue(text(panel).contains("Sign in to Kilo Code"))
            assertSame(banner, components(panel.top).filterIsInstance<InlineBanner>().single())
        }
    }

    private fun providers(): ProvidersDto = ProvidersDto(
        providers = listOf(
            ProviderDto(
                id = "kilo",
                name = "Kilo",
                models = mapOf(
                    "old" to ModelDto(id = "old", name = "Old"),
                    "new" to ModelDto(id = "new", name = "New"),
                ),
            ),
        ),
        connected = emptyList(),
        defaults = emptyMap(),
    )

    private fun select(panel: ModelsSettingsUi, id: String) {
        val picker = components(panel).filterIsInstance<ModelPicker>().first()
        picker.onSelect(ModelPicker.Item(id, id.replaceFirstChar { it.titlecase() }, "kilo", "Kilo"))
    }

    private fun requireUi(): ModelsSettingsUi = requireNotNull(ui)

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun flushUntil(done: () -> Boolean) = runBlocking {
        repeat(20) {
            delay(100)
            edt { UIUtil.dispatchAllInvocationEvents() }
            if (done()) return@runBlocking
        }
        edt { UIUtil.dispatchAllInvocationEvents() }
        assertTrue(done())
    }

    private fun text(root: Container): String {
        val out = mutableListOf<String>()
        for (comp in components(root)) {
            if (!comp.isVisible) continue
            when (comp) {
                is AbstractButton -> comp.text?.let { out.add(it) }
                is JLabel -> comp.text?.let { out.add(it) }
                is JTextComponent -> comp.text?.let { out.add(it) }
                is JTextField -> comp.text?.let { out.add(it) }
            }
        }
        return out.joinToString("\n")
    }

    private fun components(root: Container): List<java.awt.Component> = buildList {
        fun visit(comp: java.awt.Component) {
            add(comp)
            if (comp is Container) comp.components.forEach { visit(it) }
        }
        visit(root)
    }
}
