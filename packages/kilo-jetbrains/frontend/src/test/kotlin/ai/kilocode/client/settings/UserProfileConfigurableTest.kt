package ai.kilocode.client.settings

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.settings.profile.ProfileUi
import ai.kilocode.client.testing.FakeAppRpcApi
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ProfileBalanceDto
import ai.kilocode.rpc.dto.ProfileDto
import ai.kilocode.rpc.dto.ProfileOrganizationDto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

@Suppress("UnstableApiUsage")
class UserProfileConfigurableTest : BasePlatformTestCase() {

    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeAppRpcApi
    private lateinit var app: KiloAppService
    private lateinit var panel: ProfileUi
    private val urls = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        rpc = FakeAppRpcApi()
        app = KiloAppService(scope, rpc)
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY)
        edt {
            panel = ProfileUi(
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
            val combo = combos(panel).single()
            assertEquals("Acme", combo.getItemAt(1))
            assertFalse(combo.getItemAt(1).toString().contains("admin", ignoreCase = true))
            combo.selectedIndex = 1
        }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("\$25.00"))
        }
        assertEquals(listOf("org_1"), rpc.orgSelections)
    }

    fun `test logged in profile uses compact stack and copyable email`() {
        val profile = ProfileDto(
            email = "alice@test.com",
            name = "Alice",
            organizations = listOf(ProfileOrganizationDto(id = "org_1", name = "Acme", role = "MEMBER")),
            balance = ProfileBalanceDto(10.0),
        )
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY, profile = profile)
        edt { panel.update(profile, KiloAppStatusDto.READY) }

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Alice"))
            assertTrue(t, t.contains("alice@test.com"))
            assertTrue(t, t.contains("BALANCE"))
            assertTrue(t, t.contains("Refresh"))
            assertFalse(t, t.contains("Active account"))
            assertFalse(t, t.contains("Organization"))

            val mail = labels(panel).filterIsInstance<JBLabel>().first { it.text == "alice@test.com" }
            assertTrue(editorPanes(mail).isNotEmpty())

            panel.setSize(800, 600)
            layout(panel)
            val refresh = buttons(panel).first { it.text == "Refresh" }
            assertFalse(refresh.isContentAreaFilled)
            val card = refresh.parent
            val dash = buttons(panel).first { it.text == "Dashboard" }
            val cardLoc = SwingUtilities.convertPoint(card.parent, card.location, panel)
            val dashLoc = SwingUtilities.convertPoint(dash.parent, dash.location, panel)
            assertTrue(dashLoc.y >= cardLoc.y + card.height)
        }
    }

    fun `test refresh updates balance UI`() {
        val profile = ProfileDto(
            email = "alice@test.com",
            name = "Alice",
            balance = ProfileBalanceDto(10.0),
        )
        val updated = profile.copy(balance = ProfileBalanceDto(25.0))
        rpc.fakeProfile = profile
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY, profile = profile)
        edt { panel.update(profile, KiloAppStatusDto.READY) }

        edt {
            assertTrue(text(panel).contains("\$10.00"))
            rpc.fakeProfile = updated
            buttons(panel).first { it.text == "Refresh" }.doClick()
            assertTrue(text(panel).contains("Refreshing...."))
        }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("\$25.00"))
            assertTrue(t, t.contains("Refresh"))
            assertFalse(t, t.contains("Refreshing...."))
            assertTrue(buttons(panel).first { it.text == "Refresh" }.isEnabled)
        }
    }

    fun `test logged out update retains login button`() {
        edt {
            val btn = buttons(panel).first { it.text == "Login with Kilo Code" }
            panel.update(null, KiloAppStatusDto.READY)
            val btn2 = buttons(panel).first { it.text == "Login with Kilo Code" }
            assertSame(btn, btn2)
        }
    }

    fun `test account update retains name label`() {
        val alice = ProfileDto(email = "alice@test.com", name = "Alice")
        val bob = ProfileDto(email = "bob@test.com", name = "Bob")
        edt {
            panel.update(alice, KiloAppStatusDto.READY)
            val lbl = labels(panel).first { it.text == "Alice" }
            panel.update(bob, KiloAppStatusDto.READY)
            val lbl2 = labels(panel).first { it.text == "Bob" }
            assertSame(lbl, lbl2)
        }
    }

    fun `test organization switch retains combo`() {
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

        val captured = edt { combos(panel).single() }

        edt { captured.selectedIndex = 1 }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("\$25.00"))
            val same = combos(panel).single()
            assertSame(captured, same)
            assertEquals(1, same.selectedIndex)
        }
    }

    fun `test organization switch keeps account visible during transient null profile`() {
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

        edt {
            panel.update(personal, KiloAppStatusDto.READY)
            combos(panel).single().selectedIndex = 1
            panel.update(null, KiloAppStatusDto.READY)

            val t = text(panel)
            assertTrue(t, t.contains("Alice"))
            assertFalse(t, t.contains("Not logged in"))
            assertEquals(1, combos(panel).single().selectedIndex)
        }
    }

    fun `test login shows device auth card before completion`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.completeGate = CompletableDeferred()

        edt {
            buttons(panel).first { it.text == "Login with Kilo Code" }.doClick()
        }

        flushUntil { text(panel).contains("Sign in to Kilo Code") }

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Sign in to Kilo Code"))
            assertTrue(t, t.contains("STEP 1: OPEN THIS URL"))
            assertTrue(t, t.contains("https://auth.kilo.ai/device"))
            assertTrue(t, t.contains("Open Browser"))
            assertTrue(t, t.contains("STEP 2: ENTER THIS CODE"))
            assertTrue(t, t.contains("Waiting for authorization..."))
            assertTrue(t, t.contains("Cancel"))
        }

        // QR label should have an icon
        edt {
            val qr = labelsByName(panel, "kilo.login.qr").firstOrNull()
            assertNotNull(qr)
            assertNotNull(qr!!.icon)
        }

        assertEquals(listOf("https://auth.kilo.ai/device"), urls)

        // Complete login
        edt { rpc.completeGate!!.complete(Unit) }
        flushUntil { text(panel).contains("Alice") }

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Alice"))
            assertTrue(t, t.contains("alice@test.com"))
            assertTrue(buttons(panel).any { it.text == "Log Out" })
        }
    }

    fun `test cancel login invalidates stale completion`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.completeGate = CompletableDeferred()

        edt { buttons(panel).first { it.text == "Login with Kilo Code" }.doClick() }
        flushUntil { text(panel).contains("Sign in to Kilo Code") }

        // Click Cancel
        edt { buttons(panel).first { it.text == "Cancel" }.doClick() }
        flush()

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Not logged in"))
            assertTrue(buttons(panel).any { it.text == "Login with Kilo Code" })
        }

        // Now complete the gate — the stale result should be ignored
        rpc.fakeProfile = ProfileDto(email = "stale@test.com", name = "Stale")
        edt { rpc.completeGate!!.complete(Unit) }
        flush()

        edt {
            val t = text(panel)
            assertFalse(t, t.contains("Stale"))
            assertTrue(t, t.contains("Not logged in"))
        }
    }

    fun `test login failure shows retry`() {
        rpc.startError = IllegalStateException("HTTP 500 <!doctype html><body>Internal Server Error</body>")

        edt { buttons(panel).first { it.text == "Login with Kilo Code" }.doClick() }
        flushUntil { text(panel).contains("Login failed") }

        edt {
            val t = text(panel)
            assertTrue(t, t.contains("Login failed"))
            assertTrue(buttons(panel).any { it.text == "Try Again" })
        }
    }

    fun `test auth card retains qr label across sync`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.completeGate = CompletableDeferred()

        edt { buttons(panel).first { it.text == "Login with Kilo Code" }.doClick() }
        flushUntil { text(panel).contains("Sign in to Kilo Code") }

        val qrBefore = edt { labelsByName(panel, "kilo.login.qr").firstOrNull() }
        assertNotNull(qrBefore)

        // Force another sync call while still pending
        edt { panel.update(null, KiloAppStatusDto.READY) }
        flush()

        val qrAfter = edt { labelsByName(panel, "kilo.login.qr").firstOrNull() }
        assertNotNull(qrAfter)
        assertSame(qrBefore, qrAfter)

        edt { rpc.completeGate!!.complete(Unit) }
        flush()
    }

    fun `test auth card step labels are left aligned`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.completeGate = CompletableDeferred()

        edt { buttons(panel).first { it.text == "Login with Kilo Code" }.doClick() }
        flushUntil { text(panel).contains("Sign in to Kilo Code") }

        edt {
            val step1 = labels(panel).firstOrNull { it.text == "STEP 1: OPEN THIS URL" }
            val step2 = labels(panel).firstOrNull { it.text == "STEP 2: ENTER THIS CODE" }
            assertNotNull("STEP 1 label not found", step1)
            assertNotNull("STEP 2 label not found", step2)
            assertEquals("STEP 1 label should be left aligned", SwingConstants.LEFT, step1!!.horizontalAlignment)
            assertEquals("STEP 2 label should be left aligned", SwingConstants.LEFT, step2!!.horizontalAlignment)
        }

        edt { rpc.completeGate!!.complete(Unit) }
        flush()
    }

    fun `test url field selects all on click`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.completeGate = CompletableDeferred()

        edt { buttons(panel).first { it.text == "Login with Kilo Code" }.doClick() }
        flushUntil { text(panel).contains("Sign in to Kilo Code") }

        edt {
            val field = fieldsByName(panel, "kilo.login.url").firstOrNull()
            assertNotNull("URL field not found", field)
            // Verify the field has focus/mouse listeners wired for selectAll
            assertTrue("URL field should have focus listeners", field!!.focusListeners.isNotEmpty())
            assertTrue("URL field should have mouse listeners", field.mouseListeners.isNotEmpty())
        }

        edt { rpc.completeGate!!.complete(Unit) }
        flush()
    }

    fun `test balance card has card background`() {
        val profile = ProfileDto(
            email = "alice@test.com",
            name = "Alice",
            balance = ProfileBalanceDto(10.0),
        )
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY, profile = profile)
        edt { panel.update(profile, KiloAppStatusDto.READY) }

        edt {
            val card = panelsByName(panel, "kilo.profile.balanceCard").firstOrNull()
            assertNotNull("Balance card not found", card)
            assertFalse("Balance card should paint its own rounded background", card!!.isOpaque)
            assertNotNull("Balance card background should not be null", card.background)
            val inner = panels(card).filter { it !== card }
            assertTrue("Balance card internals should be transparent", inner.all { !it.isOpaque })
        }
    }

    fun `test code panel has card background`() {
        rpc.fakeProfile = ProfileDto(email = "alice@test.com", name = "Alice")
        rpc.completeGate = CompletableDeferred()

        edt { buttons(panel).first { it.text == "Login with Kilo Code" }.doClick() }
        flushUntil { text(panel).contains("Sign in to Kilo Code") }

        edt {
            val codePanel = panelsByName(panel, "kilo.login.codePanel").firstOrNull()
            assertNotNull("Code panel not found", codePanel)
            assertFalse("Code panel should paint its own rounded background", codePanel!!.isOpaque)
            assertNotNull("Code panel background should not be null", codePanel.background)
        }

        edt { rpc.completeGate!!.complete(Unit) }
        flush()
    }

    fun `test profile update does not trigger organization rpc`() {
        val orgs = listOf(ProfileOrganizationDto(id = "org_1", name = "Acme", role = "ADMIN"))
        val profile = ProfileDto(
            email = "alice@test.com",
            name = "Alice",
            organizations = orgs,
            currentOrgId = "org_1",
        )
        app._state.value = KiloAppStateDto(KiloAppStatusDto.READY, profile = profile)
        edt { panel.update(profile, KiloAppStatusDto.READY) }
        edt { panel.update(profile.copy(currentOrgId = "org_1"), KiloAppStatusDto.READY) }
        flush()
        assertTrue(rpc.orgSelections.isEmpty())
    }

    // -- helpers --

    private fun flushUntil(timeoutMs: Long = 3000, condition: () -> Boolean) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!edt { condition() }) {
            if (System.currentTimeMillis() > deadline) fail("flushUntil timed out after ${timeoutMs}ms")
            delay(50)
            edt { UIUtil.dispatchAllInvocationEvents() }
        }
    }

    private fun labelsByName(root: Container, name: String): List<JLabel> = buildList {
        for (comp in root.components) {
            if (comp is JLabel && comp.name == name) add(comp)
            if (comp is Container) addAll(labelsByName(comp, name))
        }
    }

    private fun fieldsByName(root: Container, name: String): List<JTextField> = buildList {
        for (comp in root.components) {
            if (comp is JTextField && comp.name == name) add(comp)
            if (comp is Container) addAll(fieldsByName(comp, name))
        }
    }

    private fun panelsByName(root: Container, name: String): List<JPanel> = buildList {
        for (comp in root.components) {
            if (comp is JPanel && comp.name == name) add(comp)
            if (comp is Container) addAll(panelsByName(comp, name))
        }
    }

    private fun panels(root: Container): List<JPanel> = buildList {
        if (root is JPanel) add(root)
        for (comp in root.components) {
            if (comp is Container) addAll(panels(comp))
        }
    }

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun flush() = runBlocking {
        repeat(5) {
            delay(100)
            edt { UIUtil.dispatchAllInvocationEvents() }
        }
    }

    private fun visible(comp: Component): Boolean =
        comp.isVisible && (comp.parent?.let(::visible) ?: true)

    private fun buttons(root: Container): List<AbstractButton> = buildList {
        for (comp in root.components) {
            if (!comp.isVisible) continue
            if (comp is AbstractButton) add(comp)
            if (comp is Container) addAll(buttons(comp))
        }
    }

    private fun combos(root: Container): List<JComboBox<*>> = buildList {
        for (comp in root.components) {
            if (!comp.isVisible) continue
            if (comp is JComboBox<*>) add(comp)
            if (comp is Container) addAll(combos(comp))
        }
    }

    private fun labels(root: Container): List<JLabel> = buildList {
        for (comp in root.components) {
            if (!comp.isVisible) continue
            if (comp is JLabel) add(comp)
            if (comp is Container) addAll(labels(comp))
        }
    }

    private fun layout(root: Container) {
        root.doLayout()
        for (comp in root.components) {
            if (comp is Container) layout(comp)
        }
    }

    private fun editorPanes(root: Container): List<JEditorPane> = buildList {
        for (comp in root.components) {
            if (!comp.isVisible) continue
            if (comp is JEditorPane) add(comp)
            if (comp is Container) addAll(editorPanes(comp))
        }
    }

    private fun text(root: Container): String {
        val acc = mutableListOf<String>()
        collectText(root, acc)
        return acc.joinToString("\n")
    }

    private fun collectText(root: Container, acc: MutableList<String>) {
        for (comp in root.components) {
            if (!comp.isVisible) continue
            when (comp) {
                is AbstractButton -> comp.text?.let { acc.add(it) }
                is JEditorPane -> comp.text?.let { acc.add(it) }
                is JLabel -> comp.text?.let { acc.add(it) }
                is JTextField -> comp.text?.let { acc.add(it) }
            }
            if (comp is Container) collectText(comp, acc)
        }
    }
}
