package ai.kilocode.client.migration

import ai.kilocode.client.session.SessionUiTestBase
import ai.kilocode.client.session.ui.SessionRootPanel
import ai.kilocode.client.session.ui.prompt.PromptPanel
import ai.kilocode.client.migration.ui.MigrationItemRow
import ai.kilocode.client.migration.ui.MigrationOverlayPanel
import ai.kilocode.client.migration.ui.MigrationWizardPanel
import ai.kilocode.client.ui.layout.Align
import ai.kilocode.rpc.dto.LegacyMigrationDetectionDto
import ai.kilocode.rpc.dto.MigrationProviderInfoDto
import java.awt.Rectangle

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
        layout()
        assertTrue("blocker should be visible", root.blocker.isVisible)
        assertTrue("blocker should be opaque", root.blocker.isOpaque)
        assertEquals(Rectangle(0, 0, root.width, root.height), root.blocker.bounds)
        assertEquals(1, root.blocker.componentCount)
    }

    fun `test visible migration state lays out content before resize`() {
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()

        val row = find<MigrationItemRow>(ui)
        assertTrue("migration row should be visible", row.isVisible)
        assertTrue("migration row width should be laid out before resize: ${row.bounds}", row.width > 0)
        assertTrue("migration row height should be laid out before resize: ${row.bounds}", row.height > 0)
    }

    fun `test migration opens on selection screen with keep file checked`() {
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()

        val wizard = find<MigrationWizardPanel>(ui)
        assertTrue(wizard.keepLegacySettingsFileSelectedForTest())
    }

    fun `test migration wizard is centered in overlay`() {
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()
        layout()

        val overlay = find<MigrationOverlayPanel>(ui)
        overlay.doLayout()
        val align = find<Align>(overlay)
        align.doLayout()
        val wizard = find<MigrationWizardPanel>(overlay)

        assertTrue("align wrapper should fill most overlay width", align.width > overlay.width / 2)
        assertTrue("align wrapper should fill most overlay height", align.height > overlay.height / 2)
        assertTrue("wizard should be horizontally centered: ${wizard.bounds} in ${align.bounds}", kotlin.math.abs(wizard.x - (align.width - wizard.width) / 2) <= 1)
        assertTrue("wizard should be vertically centered: ${wizard.bounds} in ${align.bounds}", kotlin.math.abs(wizard.y - (align.height - wizard.height) / 2) <= 1)
    }

    fun `test hidden state after visible hides blocker`() {
        val root = find<SessionRootPanel>(ui)
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()
        assertTrue(root.blocker.isVisible)

        fakeMigration._state.value = MigrationUiState.Hidden
        settle()
        assertFalse(root.blocker.isVisible)
        assertEquals(0, root.blocker.componentCount)
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
        val overlay = find<MigrationOverlayPanel>(ui)
        assertSame(overlay.preferredFocusComponent(), ui.defaultFocusedComponent)
        assertNotSame(find<PromptPanel>(ui).defaultFocusedComponent, ui.defaultFocusedComponent)
    }

    fun `test migration modal covers prompt with opaque background`() {
        fakeMigration._state.value = MigrationUiState.Needed(detection = sampleDetection())
        settle()
        layout()
        val root = find<SessionRootPanel>(ui)

        assertTrue(root.blocker.isVisible)
        assertTrue(root.blocker.isOpaque)
        assertEquals(Rectangle(0, 0, root.width, root.height), root.blocker.bounds)
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
