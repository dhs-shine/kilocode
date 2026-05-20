package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.shared.BaseSessionQuestionPanel
import ai.kilocode.client.session.ui.shared.applyButton
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
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
    val button = applyButton(KiloBundle.message("session.login.required.button")) { openProfile() }

    init {
        isOpaque = false
        isVisible = false

        card.headerText.text = KiloBundle.message("session.login.required.title")

        val footer = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(UiStyle.Gap.lg())
            add(button, BorderLayout.WEST)
        }

        card.setFooter(footer)

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
