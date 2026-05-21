package ai.kilocode.client.session.views.base

import ai.kilocode.client.session.ui.style.SessionUiStyle
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import javax.swing.JButton

/**
 * A [JButton] variant used inside session question/login-required panels.
 *
 * Primary buttons receive [DarculaButtonUI.DEFAULT_STYLE_KEY] so they use the
 * platform's default-button accent. Buttons keep the standard Look-and-Feel
 * border, padding, disabled state, and focus painting, while their component
 * background follows the question card surface so border/focus chrome blends
 * into the inline panel instead of the surrounding transcript.
 */
class SessionQuestionButton(text: String, val primary: Boolean) : JButton(text) {

    init {
        if (primary) {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
        syncBackground()
    }

    override fun updateUI() {
        super.updateUI()
        syncBackground()
    }

    private fun syncBackground() {
        background = SessionUiStyle.View.surface()
    }
}

/** Create a non-primary (secondary) session question button. */
fun dismissButton(text: String, action: () -> Unit): SessionQuestionButton =
    SessionQuestionButton(text, primary = false).apply { addActionListener { action() } }

/** Create a primary (default/accent) session question button. */
fun applyButton(text: String, action: () -> Unit): SessionQuestionButton =
    SessionQuestionButton(text, primary = true).apply { addActionListener { action() } }
