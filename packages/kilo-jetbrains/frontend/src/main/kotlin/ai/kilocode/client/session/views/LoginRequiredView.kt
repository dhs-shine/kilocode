package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.shared.BaseSessionQuestionPanel
import ai.kilocode.client.session.ui.shared.applyButton
import ai.kilocode.client.session.ui.shared.dismissButton
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.ui.UiStyle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Retained inline view shown at the bottom of the transcript when a session
 * enters [ai.kilocode.client.session.model.SessionState.LoginRequired].
 *
 * Mirrors the anchored placement of [PermissionView] and [question.QuestionView]:
 * it stays as a stable child inside [ai.kilocode.client.session.ui.SessionMessageListPanel]
 * and is toggled visible/hidden via [show]/[hideView].
 */
class LoginRequiredView(
    private val openProfile: () -> Unit,
    private val dismiss: () -> Unit,
) : BorderLayoutPanel(), SessionEditorStyleTarget, SessionView {

    override val sessionViewKind = SessionView.Kind.Default

    private val card = BaseSessionQuestionPanel()
    val openProfileButton = applyButton(KiloBundle.message("session.login.required.button")) { openProfile() }
    val dismissButton = dismissButton(KiloBundle.message("session.login.required.dismiss")) { dismiss() }

    init {
        isOpaque = false
        isVisible = false

        card.headerText.text = KiloBundle.message("session.login.required.title")
        card.headerText.alignmentX = Component.LEFT_ALIGNMENT
        card.descriptionText.alignmentX = Component.LEFT_ALIGNMENT

        val footer = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val actions = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(dismissButton)
            add(Box.createHorizontalStrut(UiStyle.Gap.sm()))
            add(openProfileButton)
        }
        footer.add(actions, BorderLayout.EAST)

        card.setFooter(footer)

        addToCenter(card)
    }

    /** Make the view visible with [message] shown as the description. */
    @RequiresEdt
    fun show(message: String) {
        card.descriptionText.text = message
        isVisible = true
        refresh()
    }

    /** Hide the view. */
    @RequiresEdt
    fun hideView() {
        if (!isVisible) return
        isVisible = false
        refresh()
    }

    @RequiresEdt
    override fun applyStyle(style: SessionEditorStyle) {
        card.applyStyle(style)
    }

    private fun refresh() {
        revalidate()
        repaint()
        parent?.revalidate()
        parent?.repaint()
    }
}
