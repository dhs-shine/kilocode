package ai.kilocode.client.migration.ui

import ai.kilocode.client.migration.SessionMigrationSummary
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.LegacyMigrationResultItemDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.JBCheckBox
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
 * Shows imported/skipped/errored buckets, re-import checkbox, and copy report button.
 */
class SessionMigrationSummaryPanel : JPanel(BorderLayout()) {

    private val imported = JBLabel()
    private val skipped = JBLabel()
    private val errored = JBLabel()
    private val reimportAll = JBCheckBox(KiloBundle.message("migration.session.summary.reimport.all"))
    private val copyBtn = JButton(KiloBundle.message("migration.session.summary.copy.report"), AllIcons.Actions.Copy)

    private var skippedItems: List<LegacyMigrationResultItemDto> = emptyList()
    private var selectedForReimport: MutableSet<String> = mutableSetOf()
    private val reimportCheckboxes = mutableListOf<Pair<JBCheckBox, String>>()
    private val skippedPanel = JPanel(GridBagLayout()).apply { isOpaque = false }
    private val feedbackLabel = JBLabel(KiloBundle.message("migration.session.summary.copied")).apply {
        isVisible = false
        foreground = UiStyle.Colors.weak()
    }

    var onForceReimport: ((List<String>) -> Unit)? = null

    private val forceBtn = JButton(KiloBundle.message("migration.session.summary.force.reimport")).apply {
        isEnabled = false
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Gap.sm())

        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
            add(imported)
            add(skipped)
            add(errored)
        }

        reimportAll.isOpaque = false
        reimportAll.addActionListener { toggleAll(reimportAll.isSelected) }

        forceBtn.addActionListener { onForceReimport?.invoke(selectedForReimport.toList()) }

        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
            isOpaque = false
            add(forceBtn)
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
            add(skippedPanel, gc)
            add(reimportAll, gc)
            add(btnRow, gc)
        }
        add(center, BorderLayout.CENTER)
    }

    fun update(summary: SessionMigrationSummary) {
        imported.text = KiloBundle.message("migration.session.summary.imported", summary.imported.size)
        skipped.text = KiloBundle.message("migration.session.summary.skipped", summary.skipped.size)
        errored.text = KiloBundle.message("migration.session.summary.errored", summary.errored.size)

        skippedItems = summary.skipped
        selectedForReimport.clear()
        skippedPanel.removeAll()
        reimportCheckboxes.clear()

        val gc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
        }
        for (item in summary.skipped) {
            val cb = JBCheckBox(item.item)
            cb.isOpaque = false
            cb.addActionListener {
                if (cb.isSelected) selectedForReimport.add(item.item) else selectedForReimport.remove(item.item)
                forceBtn.isEnabled = selectedForReimport.isNotEmpty()
                reimportAll.isSelected = selectedForReimport.size == skippedItems.size
            }
            reimportCheckboxes.add(cb to item.item)
            skippedPanel.add(cb, gc)
        }

        reimportAll.isVisible = summary.skipped.isNotEmpty()
        forceBtn.isEnabled = false
        feedbackLabel.isVisible = false
        skippedPanel.revalidate()
        skippedPanel.repaint()
    }

    private fun toggleAll(checked: Boolean) {
        for ((cb, id) in reimportCheckboxes) {
            cb.isSelected = checked
            if (checked) selectedForReimport.add(id) else selectedForReimport.remove(id)
        }
        forceBtn.isEnabled = checked && reimportCheckboxes.isNotEmpty()
    }

    private fun buildReport(): String {
        val sb = StringBuilder()
        sb.appendLine(KiloBundle.message("migration.session.summary.imported", skippedItems.size))
        for (item in skippedItems) {
            sb.appendLine("  - ${item.item}: ${item.message ?: "skipped"}")
        }
        return sb.toString()
    }
}
