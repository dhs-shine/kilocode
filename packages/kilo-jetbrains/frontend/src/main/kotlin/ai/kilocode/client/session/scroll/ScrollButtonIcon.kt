package ai.kilocode.client.session.scroll

import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.colorizedSvgIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.Icon

internal object ScrollButtonIcon {
    private const val BOTTOM = "/icons/scroll-bottom.svg"
    private const val PROMPT = "/icons/scroll-question.svg"

    fun create(question: Boolean = false): Icon {
        if (question) {
            return colorizedSvgIcon(
                path = PROMPT,
                owner = ScrollButtonIcon::class.java,
                fillColor = UiStyle.Colors.warningLabelForeground(),
                borderColor = Color.WHITE,
                fillColors = listOf(SessionUiStyle.ScrollIcon.QUESTION),
                borderColors = listOf(SessionUiStyle.ScrollIcon.FOREGROUND),
            )
        }

        return colorizedSvgIcon(
            path = BOTTOM,
            owner = ScrollButtonIcon::class.java,
            fillColor = JBUI.CurrentTheme.Button.defaultButtonColorStart(),
            borderColor = JBUI.CurrentTheme.Button.defaultButtonForeground(),
            fillColors = listOf(SessionUiStyle.ScrollIcon.BOTTOM_LIGHT, SessionUiStyle.ScrollIcon.BOTTOM_DARK),
            borderColors = listOf(SessionUiStyle.ScrollIcon.FOREGROUND),
        )
    }
}
