package ai.kilocode.client.session.scroll

import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.colorizedSvgIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.Icon

internal object ScrollButtonIcon {
    private val bottom = colorizedSvgIcon(
        path = "/icons/scroll-bottom.svg",
        owner = ScrollButtonIcon::class.java,
        fillColor = JBUI.CurrentTheme.Button.defaultButtonColorStart(),
        borderColor = JBUI.CurrentTheme.Button.defaultButtonForeground(),
        fillColors = listOf(SessionUiStyle.ScrollIcon.BOTTOM_LIGHT, SessionUiStyle.ScrollIcon.BOTTOM_DARK),
        borderColors = listOf(SessionUiStyle.ScrollIcon.FOREGROUND),
    )
    private val prompt = colorizedSvgIcon(
        path = "/icons/scroll-question.svg",
        owner = ScrollButtonIcon::class.java,
        fillColor = UiStyle.Colors.warningLabelForeground(),
        borderColor = Color.WHITE,
        fillColors = listOf(SessionUiStyle.ScrollIcon.QUESTION),
        borderColors = listOf(SessionUiStyle.ScrollIcon.FOREGROUND),
    )

    fun create(question: Boolean = false): Icon {
        if (question) return prompt
        return bottom
    }
}
