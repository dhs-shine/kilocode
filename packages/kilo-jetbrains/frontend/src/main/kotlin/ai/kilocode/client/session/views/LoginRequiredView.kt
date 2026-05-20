package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.shared.BaseSessionQuestionPanel
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.FlowLayout
import javax.swing.JButton
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
) : BorderLayoutPanel(), SessionEditorStyleTarget, SessionView {

    override val sessionViewKind = SessionView.Kind.Default

    private val card = BaseSessionQuestionPanel()
    private val button = JButton(KiloBundle.message("session.login.required.button"))

    init {
        isOpaque = false
        isVisible = false

        card.headerText.text = KiloBundle.message("session.login.required.title")

        button.addActionListener { openProfile() }

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        actions.isOpaque = false
        actions.add(button)

        card.setFooter(actions)

        addToCenter(card)
    }

    /** Make the view visible with [message] shown as the description. */
    fun show(message: String) {
        card.descriptionText.text = message
        isVisible = true
        refresh()
    }

    /** Hide the view. */
    fun hideView() {
        if (!isVisible) return
        isVisible = false
        refresh()
    }

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
