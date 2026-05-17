package ai.kilocode.client.settings

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.testing.FakeAppRpcApi
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ProfileBalanceDto
import ai.kilocode.rpc.dto.ProfileDto
import ai.kilocode.rpc.dto.ProfileOrganizationDto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JLabel

@Suppress("UnstableApiUsage")
class UserProfileConfigurableTest : BasePlatformTestCase() {

    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeAppRpcApi
    private lateinit var app: KiloAppService
    private lateinit var panel: ProfilePanel
    private val urls = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        rpc = FakeAppRpcApi()
        app = KiloAppService(scope, rpc)
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY)
        edt {
            panel = ProfilePanel(
                profile = null,
                status = KiloAppStatusDto.READY,
                cs = scope,
                app = app,
                browse = { urls.add(it) },
            )
        }
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test login updates profile UI`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")

        edt {
            assertTrue(text(panel).contains("Not logged in"))
            buttons(panel).first { it.text == "Login with Kilo Code" }.doClick()
        }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Alice"))
            assertTrue(t, t.contains("alice@test.com"))
            assertTrue(buttons(panel).any { it.text == "Log Out" })
        }
        assertEquals(listOf("https://auth.kilo.ai/device"), urls)
    }

    fun `test logout updates profile UI`() {
        val profile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.fakeProfile = profile
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY, profile = profile)
        edt { panel.update(profile, KiloAppStatusDto.READY) }

        edt {
            assertTrue(buttons(panel).any { it.text == "Log Out" })
            buttons(panel).first { it.text == "Log Out" }.doClick()
        }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Not logged in"))
            assertTrue(buttons(panel).any { it.text == "Login with Kilo Code" })
        }
    }

    fun `test organization switch updates balance UI`() {
        val orgs = listOf(ProfileOrganizationDto(id = "org_1", name = "Acme", role = "ADMIN"))
        val personal = ProfileDto(
            email = "alice@test.com",
            name = "Alice",
            organizations = orgs,
            balance = ProfileBalanceDto(10.0),
        )
        val org = personal.copy(balance = ProfileBalanceDto(25.0), currentOrgId = "org_1")
        rpc.fakeProfile = personal
        rpc.orgProfiles["org_1"] = org
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY, profile = personal)
        edt { panel.update(personal, KiloAppStatusDto.READY) }

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("\$10.00"))
            combos(panel).single().selectedIndex = 1
        }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("\$25.00"))
        }
        assertEquals(listOf("org_1"), rpc.orgSelections)
    }

    private fun edt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeAndWait(block)
    }

    private fun flush() = runBlocking {
        repeat(5) {
            delay(100)
            edt { UIUtil.dispatchAllInvocationEvents() }
        }
    }

    private fun buttons(root: Container): List<AbstractButton> = root.components.flatMap { comp ->
        val item = if (comp is AbstractButton) listOf(comp) else emptyList()
        if (comp is Container) item + buttons(comp) else item
    }

    private fun combos(root: Container): List<JComboBox<*>> = root.components.flatMap { comp ->
        val item = if (comp is JComboBox<*>) listOf(comp) else emptyList()
        if (comp is Container) item + combos(comp) else item
    }

    private fun text(root: Container): String {
        val acc = mutableListOf<String>()
        collectText(root, acc)
        return acc.joinToString("\n")
    }

    private fun collectText(root: Container, acc: MutableList<String>) {
        for (comp in root.components) {
            when (comp) {
                is AbstractButton -> comp.text?.let { acc.add(it) }
                is JLabel -> comp.text?.let { acc.add(it) }
            }
            if (comp is Container) collectText(comp, acc)
        }
    }
}
