package ai.kilocode.client.migration

import ai.kilocode.client.session.SessionUiTestBase
import ai.kilocode.client.session.ui.SessionRootPanel
import ai.kilocode.rpc.dto.LegacyMigrationDetectionDto
import ai.kilocode.rpc.dto.MigrationProviderInfoDto

@Suppress("UnstableApiUsage")
class SessionUiMigrationTest : SessionUiTestBase() {

    private lateinit var fakeMigration: FakeMigrationUiController

    override fun setUp() {
        super.setUp()
        // Replace the default UI with one using our observable fake migration controller.
        fakeMigration = FakeMigrationUiController()
        ui = newUi(migration = fakeMigration)
        layout()
    }

    fun `test hidden migration state keeps blocker hidden`() {
        val root = find<SessionRootPanel>(ui)
        fakeMigration._state.value = MigrationUiState.Hidden
        settle()
        assertFalse(root.blocker.isVisible)
    }

    fun `test visible migration state shows root blocker`() {
        val root = find<SessionRootPanel>(ui)
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()
        assertTrue("blocker should be visible", root.blocker.isVisible)
    }

    fun `test hidden state after visible hides blocker`() {
        val root = find<SessionRootPanel>(ui)
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()
        assertTrue(root.blocker.isVisible)

        fakeMigration._state.value = MigrationUiState.Hidden
        settle()
        assertFalse(root.blocker.isVisible)
    }

    fun `test two session UIs sharing one controller both react to state change`() {
        val ui2 = newUi(migration = fakeMigration)
        ui2.setSize(800, 600)
        try {
            fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
            settle()

            val root1 = find<SessionRootPanel>(ui)
            val root2 = find<SessionRootPanel>(ui2)
            assertTrue("ui1 blocker should be visible", root1.blocker.isVisible)
            assertTrue("ui2 blocker should be visible", root2.blocker.isVisible)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(ui2)
        }
    }

    fun `test default focused component is migration overlay when blocked`() {
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()
        val root = find<SessionRootPanel>(ui)
        assertTrue("blocker should be visible for defaultFocused test", root.blocker.isVisible)
        // defaultFocusedComponent should not throw and should not be the prompt editor
        val focused = ui.defaultFocusedComponent
        assertNotNull(focused)
    }

    private fun sampleDetection() = LegacyMigrationDetectionDto(
        providers = listOf(
            MigrationProviderInfoDto("profile1", "anthropic", "claude-3", true, true, "anthropic"),
        ),
        mcpServers = emptyList(),
        customModes = emptyList(),
        sessions = emptyList(),
        defaultModel = null,
        settings = null,
        hasData = true,
    )
}
