package ai.kilocode.client.migration.ui

import ai.kilocode.client.migration.MigrationUiSelections
import ai.kilocode.client.migration.MigrationUiState
import ai.kilocode.client.ui.UiStyle
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Outer container for the migration wizard rendered inside the blocker layer.
 *
 * Wraps [MigrationWizardPanel] in a bordered overlay with a panel background.
 * Build once; call [update] on every state change.
 */
class MigrationOverlayPanel : JBPanel<MigrationOverlayPanel>(BorderLayout()) {

    private val wizard = MigrationWizardPanel()

    var onSkip: (() -> Unit)?
        get() = wizard.onSkip
        set(v) { wizard.onSkip = v }

    var onStart: ((MigrationUiSelections) -> Unit)?
        get() = wizard.onStart
        set(v) { wizard.onStart = v }

    var onForce: ((List<String>) -> Unit)?
        get() = wizard.onForce
        set(v) { wizard.onForce = v }

    var onDone: (() -> Unit)?
        get() = wizard.onDone
        set(v) { wizard.onDone = v }

    var onContinueFromError: (() -> Unit)?
        get() = wizard.onContinueFromError
        set(v) { wizard.onContinueFromError = v }

    init {
        withBackground(UiStyle.Colors.bg())
        border = JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 1)
        add(wizard, BorderLayout.CENTER)
    }

    fun update(state: MigrationUiState.Needed) {
        wizard.update(state)
    }

    fun preferredFocusComponent(): JComponent = wizard.preferredFocusComponent()
}
