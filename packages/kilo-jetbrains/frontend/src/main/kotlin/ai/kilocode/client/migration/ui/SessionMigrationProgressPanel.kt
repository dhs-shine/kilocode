package ai.kilocode.client.migration.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.LegacyMigrationSessionProgressDto
import ai.kilocode.rpc.dto.MigrationSessionPhaseDto
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JPanel

/**
 * Shows live session migration progress (current/total, metadata, step labels).
 * Summary phase delegates rendering to [SessionMigrationSummaryPanel].
 */
class SessionMigrationProgressPanel : JPanel(GridBagLayout()) {

    private val header = JBLabel().apply { font = font.deriveFont(java.awt.Font.BOLD) }
    private val directory = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    private val title = JBLabel()
    private val date = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    private val preparingIcon = MigrationStatusIcon()
    private val storingIcon = MigrationStatusIcon()
    private val preparingLabel = JBLabel(KiloBundle.message("migration.session.step.preparing"))
    private val storingLabel = JBLabel(KiloBundle.message("migration.session.step.storing"))
    private val dateFormat = SimpleDateFormat("HH:mm MM/dd/yyyy")

    init {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Gap.sm())

        val gc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
        }

        add(header, gc)
        add(directory, gc)
        add(title, gc)
        add(date, gc)

        val stepPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
        }
        val stepPanel2 = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
        }
        stepPanel.add(preparingIcon)
        stepPanel.add(preparingLabel)
        stepPanel2.add(storingIcon)
        stepPanel2.add(storingLabel)

        val stepsPanel = BorderLayoutPanel().apply {
            isOpaque = false
            addToTop(stepPanel)
            addToCenter(stepPanel2)
        }
        add(stepsPanel, gc)
    }

    fun update(progress: LegacyMigrationSessionProgressDto) {
        header.text = KiloBundle.message("migration.session.header", progress.index + 1, progress.total)
        val info = progress.session
        directory.text = info?.directory?.takeIf { it.isNotEmpty() } ?: KiloBundle.message("migration.session.unknown.path")
        title.text = info?.title?.takeIf { it.isNotEmpty() } ?: KiloBundle.message("migration.session.unknown.title")
        date.text = if ((info?.time ?: 0L) > 0L) dateFormat.format(Date(info!!.time)) else KiloBundle.message("migration.session.unknown.date")

        val phase = progress.phase
        when (phase) {
            MigrationSessionPhaseDto.preparing -> {
                preparingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.migrating)
                storingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.migrating)
                storingLabel.foreground = UiStyle.Colors.weak()
            }
            MigrationSessionPhaseDto.storing -> {
                preparingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.success)
                storingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.migrating)
                storingLabel.foreground = UiStyle.Colors.fg()
            }
            MigrationSessionPhaseDto.skipped -> {
                preparingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.success)
                storingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.warning)
                storingLabel.text = KiloBundle.message("migration.session.step.skipped")
            }
            MigrationSessionPhaseDto.done -> {
                preparingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.success)
                storingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.success)
            }
            MigrationSessionPhaseDto.error -> {
                preparingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.error)
                storingIcon.update(ai.kilocode.rpc.dto.MigrationItemProgressStatusDto.error)
            }
            MigrationSessionPhaseDto.summary -> { /* handled by summary panel */ }
        }
    }
}
