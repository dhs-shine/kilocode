package ai.kilocode.client.migration.ui

import ai.kilocode.client.migration.MigrationItemUiProgress
import ai.kilocode.client.migration.MigrationUiPhase
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.MigrationItemCategoryDto
import ai.kilocode.rpc.dto.MigrationItemProgressStatusDto
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * A single row in the migration item list.
 * Shows a checkbox in [MigrationUiPhase.selecting] or a status icon otherwise.
 */
class MigrationItemRow(
    private val label: String,
    private val category: MigrationItemCategoryDto,
) : BorderLayoutPanel() {

    private val check = JBCheckBox(label)
    private val statusIcon = MigrationStatusIcon()
    private val nameLabel = JBLabel(label)
    private val messageLabel = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        border = JBUI.Borders.emptyLeft(UiStyle.Gap.sm())
    }

    private val selectRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        add(check)
    }
    private val progressRow = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), 0)).apply {
        isOpaque = false
        add(statusIcon)
        add(nameLabel)
        add(messageLabel)
    }

    var selected: Boolean
        get() = check.isSelected
        set(v) { check.isSelected = v }

    var onSelectionChanged: ((Boolean) -> Unit)? = null

    init {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(UiStyle.Gap.xs())

        check.isOpaque = false
        check.addActionListener { onSelectionChanged?.invoke(check.isSelected) }

        addToCenter(selectRow)
        progressRow.isVisible = false
        add(progressRow, java.awt.BorderLayout.SOUTH)
    }

    fun updatePhase(phase: MigrationUiPhase) {
        val selecting = phase == MigrationUiPhase.selecting
        selectRow.isVisible = selecting
        progressRow.isVisible = !selecting
    }

    fun updateProgress(progress: MigrationItemUiProgress?) {
        if (progress == null) {
            statusIcon.update(MigrationItemProgressStatusDto.migrating)
            messageLabel.text = null
            return
        }
        statusIcon.update(progress.status)
        messageLabel.text = progress.message
    }
}
