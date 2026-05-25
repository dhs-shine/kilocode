package ai.kilocode.client.migration.ui

import ai.kilocode.client.migration.SessionMigrationSummary
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Renders session summary after migration completes.
 * Shows imported/errored buckets and copy report button.
 */
class SessionMigrationSummaryPanel : JPanel(BorderLayout()) {

    private val imported = JBLabel()
    private val errored = JBLabel()
    private val copyBtn = JButton(KiloBundle.message("migration.session.summary.copy.report"), AllIcons.Actions.Copy)

    private var summary = SessionMigrationSummary()
    private val feedbackLabel = JBLabel(KiloBundle.message("migration.session.summary.copied")).apply {
        isVisible = false
        foreground = UiStyle.Colors.weak()
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Gap.sm())

        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
            add(imported)
            add(errored)
        }

        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
            add(copyBtn)
            add(feedbackLabel)
        }

        copyBtn.addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(buildReport()))
            feedbackLabel.isVisible = true
        }

        val center = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val gc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridx = 0
            }
            add(statsPanel, gc)
            add(btnRow, gc)
        }
        add(center, BorderLayout.CENTER)
    }

    fun update(summary: SessionMigrationSummary) {
        this.summary = summary
        imported.text = KiloBundle.message("migration.session.summary.imported", summary.imported.size)
        errored.text = KiloBundle.message("migration.session.summary.errored", summary.errored.size)

        feedbackLabel.isVisible = false
    }

    private fun buildReport(): String {
        val sb = StringBuilder()
        sb.appendLine(KiloBundle.message("migration.session.summary.imported", summary.imported.size))
        for (item in summary.imported) {
            sb.appendLine("  - ${item.item}")
        }
        sb.appendLine(KiloBundle.message("migration.session.summary.errored", summary.errored.size))
        for (item in summary.errored) {
            sb.appendLine("  - ${item.item}: ${item.message ?: "error"}")
        }
        return sb.toString()
    }
}
