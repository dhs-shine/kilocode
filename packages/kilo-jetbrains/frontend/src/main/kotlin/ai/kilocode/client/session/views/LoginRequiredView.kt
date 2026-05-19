package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
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

    private val title = JBLabel(KiloBundle.message("session.login.required.title"))
    private val body = JBLabel(KiloBundle.message("session.login.required.description"))
    private val button = JButton(KiloBundle.message("session.login.required.button"))

    init {
        isOpaque = false
        isVisible = false

        body.foreground = UiStyle.Colors.weak()
        body.setCopyable(false)

        button.addActionListener { openProfile() }

        val card = BorderLayoutPanel()
        card.isOpaque = true
        card.background = SessionUiStyle.View.surface()
        card.border = JBUI.Borders.compound(
            SessionUiStyle.View.card(),
            JBUI.Borders.empty(
                SessionUiStyle.View.CARD_VERTICAL_PADDING,
                SessionUiStyle.View.CARD_HORIZONTAL_PADDING,
            ),
        )

        val content = JPanel(BorderLayout(0, UiStyle.Gap.sm()))
        content.isOpaque = false
        content.add(title, BorderLayout.NORTH)
        content.add(body, BorderLayout.CENTER)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        actions.isOpaque = false
        actions.add(button)

        card.add(content, BorderLayout.CENTER)
        card.add(actions, BorderLayout.SOUTH)

        addToCenter(card)
    }

    /** Make the view visible. The message is already set via bundle strings. */
    fun show(message: String) {
        body.text = message
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
        // Body foreground is theme-derived and recalculated on repaint; no font
        // overrides needed since this view does not use editor-derived fonts.
    }

    private fun refresh() {
        revalidate()
        repaint()
        parent?.revalidate()
        parent?.repaint()
    }
}
