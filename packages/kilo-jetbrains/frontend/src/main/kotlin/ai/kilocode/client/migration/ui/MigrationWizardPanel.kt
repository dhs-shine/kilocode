package ai.kilocode.client.migration.ui

import ai.kilocode.client.migration.MigrationItemUiProgress
import ai.kilocode.client.migration.MigrationSelectionBuilder
import ai.kilocode.client.migration.MigrationSettingsUiSelections
import ai.kilocode.client.migration.MigrationUiPhase
import ai.kilocode.client.migration.MigrationUiSelections
import ai.kilocode.client.migration.MigrationUiState
import ai.kilocode.client.migration.SessionMigrationSummary
import ai.kilocode.client.migration.groupStatus
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.LegacyMigrationDetectionDto
import ai.kilocode.rpc.dto.LegacyMigrationSessionProgressDto
import ai.kilocode.rpc.dto.MigrationItemCategoryDto
import ai.kilocode.rpc.dto.MigrationItemProgressStatusDto
import ai.kilocode.rpc.dto.MigrationSessionPhaseDto
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JButton as JBtn

private const val CARD_WHATS_NEW = "whats-new"
private const val CARD_MIGRATE = "migrate"

/**
 * Two-screen migration wizard: "What's New" → "Migrate Your Settings".
 *
 * Build once; call [update] for every state change. Does not rebuild the component tree.
 */
class MigrationWizardPanel : JPanel(BorderLayout()) {

    // ------ Callbacks ------
    var onSkip: (() -> Unit)? = null
    var onStart: ((MigrationUiSelections) -> Unit)? = null
    var onForce: ((List<String>) -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onContinueFromError: (() -> Unit)? = null

    // ------ Card layout ------
    private val cards = CardLayout()
    private val cardPanel = JPanel(cards)

    // ------ What's New screen ------
    private val whatsNewPanel = buildWhatsNewPanel()

    // ------ Migrate screen row state ------
    private val rows = mutableMapOf<MigrationItemCategoryDto, MigrationItemRow>()
    private val settingsRow = MigrationItemRow(KiloBundle.message("migration.row.settings"), MigrationItemCategoryDto.settings)
    private val providerRow = MigrationItemRow(KiloBundle.message("migration.row.providers"), MigrationItemCategoryDto.provider)
    private val mcpRow = MigrationItemRow(KiloBundle.message("migration.row.mcp"), MigrationItemCategoryDto.mcpServer)
    private val modesRow = MigrationItemRow(KiloBundle.message("migration.row.modes"), MigrationItemCategoryDto.customMode)
    private val sessionsRow = MigrationItemRow(KiloBundle.message("migration.row.sessions"), MigrationItemCategoryDto.session)
    private val modelRow = MigrationItemRow(KiloBundle.message("migration.row.model"), MigrationItemCategoryDto.defaultModel)

    private val sessionProgress = SessionMigrationProgressPanel()
    private val sessionSummary = SessionMigrationSummaryPanel()

    private val migrateBtn = JButton(KiloBundle.message("migration.button.migrate"))
    private val backBtn = JButton(KiloBundle.message("migration.button.back"))
    private val skipBtn = JButton(KiloBundle.message("migration.button.skip"))
    private val doneBtn = JButton(KiloBundle.message("migration.button.done"))
    private val continueBtn = JButton(KiloBundle.message("migration.button.continue"))

    private val emptyLabel = JBLabel(KiloBundle.message("migration.empty")).apply {
        foreground = UiStyle.Colors.weak()
    }

    private var detection: LegacyMigrationDetectionDto? = null
    private var selections = MigrationUiSelections()

    init {
        isOpaque = false

        rows[MigrationItemCategoryDto.provider] = providerRow
        rows[MigrationItemCategoryDto.mcpServer] = mcpRow
        rows[MigrationItemCategoryDto.customMode] = modesRow
        rows[MigrationItemCategoryDto.session] = sessionsRow
        rows[MigrationItemCategoryDto.defaultModel] = modelRow
        rows[MigrationItemCategoryDto.settings] = settingsRow

        for (row in rows.values) {
            row.onSelectionChanged = { _ -> updateMigrateButtonEnabled() }
        }

        migrateBtn.addActionListener { onStart?.invoke(currentSelections()) }
        backBtn.addActionListener { cards.show(cardPanel, CARD_WHATS_NEW) }
        skipBtn.addActionListener { onSkip?.invoke() }
        doneBtn.addActionListener { onDone?.invoke() }
        continueBtn.addActionListener { onContinueFromError?.invoke() }

        sessionSummary.onForceReimport = { ids -> onForce?.invoke(ids) }

        cardPanel.isOpaque = false
        cardPanel.add(whatsNewPanel, CARD_WHATS_NEW)
        cardPanel.add(buildMigratePanel(), CARD_MIGRATE)

        add(cardPanel, BorderLayout.CENTER)

        cards.show(cardPanel, CARD_WHATS_NEW)
    }

    // ------ Public update ------

    fun update(state: MigrationUiState.Needed) {
        val det = state.detection
        // Detection and default selections are set once on first update and are stable for the
        // lifetime of a single wizard session. The service never re-detects mid-session;
        // force re-import uses existing session IDs without changing detection data.
        if (detection == null || detection != det) {
            detection = det
            selections = MigrationSelectionBuilder.defaults(det)
            applyDefaults(det)
        }

        val phase = state.phase

        // Update row visibility based on what data exists
        providerRow.isVisible = det.providers.any { it.supported }
        mcpRow.isVisible = det.mcpServers.isNotEmpty()
        modesRow.isVisible = det.customModes.isNotEmpty()
        sessionsRow.isVisible = det.sessions.isNotEmpty()
        modelRow.isVisible = det.defaultModel != null
        settingsRow.isVisible = det.settings != null
        emptyLabel.isVisible = !det.hasData

        // Update phase for all rows
        for (row in rows.values) {
            row.updatePhase(phase)
        }

        // Update progress for each row category
        updateRowProgress(MigrationItemCategoryDto.provider, state.progress)
        updateRowProgress(MigrationItemCategoryDto.mcpServer, state.progress)
        updateRowProgress(MigrationItemCategoryDto.customMode, state.progress)
        updateRowProgress(MigrationItemCategoryDto.session, state.progress)
        updateRowProgress(MigrationItemCategoryDto.defaultModel, state.progress)
        updateRowProgress(MigrationItemCategoryDto.settings, state.progress)

        // Session progress/summary
        val sp = state.sessionProgress
        if (sp != null && sp.phase != MigrationSessionPhaseDto.summary) {
            sessionProgress.update(sp)
            sessionProgress.isVisible = true
            sessionSummary.isVisible = false
        } else if (sp != null && sp.phase == MigrationSessionPhaseDto.summary) {
            sessionSummary.update(state.sessionSummary)
            sessionProgress.isVisible = false
            sessionSummary.isVisible = true
        } else {
            sessionProgress.isVisible = false
            sessionSummary.isVisible = false
        }

        updateButtons(phase, state.running)
        updateMigrateButtonEnabled()
    }

    fun preferredFocusComponent() = migrateBtn

    // ------ Internal helpers ------

    private fun applyDefaults(det: LegacyMigrationDetectionDto) {
        val defaults = MigrationSelectionBuilder.defaults(det)
        providerRow.selected = defaults.providers.isNotEmpty()
        mcpRow.selected = defaults.mcpServers.isNotEmpty()
        modesRow.selected = defaults.customModes.isNotEmpty()
        sessionsRow.selected = defaults.sessions.isNotEmpty()
        modelRow.selected = defaults.defaultModel
        settingsRow.selected = defaults.settings.autoApproval.commandRules ||
                defaults.settings.autoApproval.readPermission ||
                defaults.settings.autoApproval.writePermission ||
                defaults.settings.autoApproval.executePermission ||
                defaults.settings.autoApproval.mcpPermission ||
                defaults.settings.autoApproval.taskPermission ||
                defaults.settings.language ||
                defaults.settings.autocomplete
    }

    private fun updateRowProgress(category: MigrationItemCategoryDto, items: List<MigrationItemUiProgress>) {
        val row = rows[category] ?: return
        val categoryItems = items.filter { it.category == category }
        if (categoryItems.isEmpty()) {
            row.updateProgress(null)
            return
        }
        val status = groupStatus(categoryItems)
        row.updateProgress(MigrationItemUiProgress(category.name, category, status))
    }

    private fun updateButtons(phase: MigrationUiPhase, running: Boolean) {
        backBtn.isVisible = phase == MigrationUiPhase.selecting
        skipBtn.isVisible = phase == MigrationUiPhase.selecting
        migrateBtn.isVisible = phase == MigrationUiPhase.selecting || phase == MigrationUiPhase.migrating
        migrateBtn.isEnabled = !running && phase == MigrationUiPhase.selecting
        migrateBtn.text = if (running) KiloBundle.message("migration.button.migrating") else KiloBundle.message("migration.button.migrate")
        doneBtn.isVisible = phase == MigrationUiPhase.done
        continueBtn.isVisible = phase == MigrationUiPhase.error
    }

    private fun updateMigrateButtonEnabled() {
        val any = rows.values.any { it.isVisible && it.selected }
        migrateBtn.isEnabled = any && migrateBtn.text == KiloBundle.message("migration.button.migrate")
    }

    private fun currentSelections(): MigrationUiSelections {
        val det = detection ?: return MigrationUiSelections()
        val providers = if (providerRow.selected) det.providers.filter { it.supported && it.hasApiKey }.map { it.profileName } else emptyList()
        val mcpServers = if (mcpRow.selected) det.mcpServers.map { it.name } else emptyList()
        val modes = if (modesRow.selected) det.customModes.map { it.slug } else emptyList()
        val sessions = if (sessionsRow.selected) det.sessions.map { it.id } else emptyList()
        val defaults = MigrationSelectionBuilder.defaults(det)
        return MigrationUiSelections(
            providers = providers,
            mcpServers = mcpServers,
            customModes = modes,
            sessions = sessions,
            defaultModel = modelRow.selected,
            settings = if (settingsRow.selected) defaults.settings else MigrationSettingsUiSelections(),
        )
    }

    private fun buildWhatsNewPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply { isOpaque = false }

        val title = JBLabel(KiloBundle.message("migration.whats_new.title")).apply {
            font = JBFont.h2().asBold()
            border = JBUI.Borders.emptyBottom(UiStyle.Gap.sm())
        }
        val subtitle = JBLabel(KiloBundle.message("migration.whats_new.subtitle")).apply {
            foreground = UiStyle.Colors.weak()
            border = JBUI.Borders.emptyBottom(UiStyle.Gap.md())
        }

        val features = listOf(
            KiloBundle.message("migration.whats_new.feature.performance"),
            KiloBundle.message("migration.whats_new.feature.interface"),
            KiloBundle.message("migration.whats_new.feature.agent_manager"),
            KiloBundle.message("migration.whats_new.feature.foundation"),
        )

        val featurePanel = JPanel(GridBagLayout()).apply { isOpaque = false }
        val gc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
        }
        for (f in features) {
            val row = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.xs(), 0)).apply {
                isOpaque = false
                add(JBLabel(AllIcons.General.InspectionsOK))
                add(JBLabel(f))
            }
            featurePanel.add(row, gc)
        }

        val content = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
            val c = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; gridx = 0 }
            add(title, c)
            add(subtitle, c)
            add(featurePanel, c)
        }

        val continueWnBtn = JButton(KiloBundle.message("migration.button.continue_to_migrate")).apply {
            addActionListener { cards.show(cardPanel, CARD_MIGRATE) }
        }
        val footer = JPanel(FlowLayout(FlowLayout.RIGHT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
            add(continueWnBtn)
        }

        panel.add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        panel.add(footer, BorderLayout.SOUTH)
        return panel
    }

    private fun buildMigratePanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply { isOpaque = false }

        val title = JBLabel(KiloBundle.message("migration.migrate.title")).apply {
            font = JBFont.h2().asBold()
            border = JBUI.Borders.emptyBottom(UiStyle.Gap.sm())
        }
        val subtitle = JBLabel(KiloBundle.message("migration.migrate.subtitle")).apply {
            foreground = UiStyle.Colors.weak()
            border = JBUI.Borders.emptyBottom(UiStyle.Gap.md())
        }
        val sectionLabel = JBLabel(KiloBundle.message("migration.migrate.section")).apply {
            font = JBFont.medium()
            border = JBUI.Borders.emptyBottom(UiStyle.Gap.xs())
        }

        val rowsPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val gc = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; gridx = 0 }
            add(emptyLabel, gc)
            add(providerRow, gc)
            add(mcpRow, gc)
            add(modesRow, gc)
            add(sessionsRow, gc)
            add(modelRow, gc)
            add(settingsRow, gc)
            add(sessionProgress, gc)
            add(sessionSummary, gc)
        }

        val content = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
            val gc = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; gridx = 0 }
            add(title, gc)
            add(subtitle, gc)
            add(sectionLabel, gc)
            add(rowsPanel, gc)
        }

        val footer = JPanel(FlowLayout(FlowLayout.RIGHT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
            add(backBtn)
            add(skipBtn)
            add(migrateBtn)
            add(doneBtn)
            add(continueBtn)
        }

        panel.add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        panel.add(footer, BorderLayout.SOUTH)
        return panel
    }
}
