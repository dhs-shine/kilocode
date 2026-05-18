package ai.kilocode.client.settings.profile

import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints

internal class ProfileCardPanel(
    top: Int,
    left: Int,
    bottom: Int = top,
    right: Int = left,
) : BorderLayoutPanel() {

    init {
        isOpaque = false
        background = UiStyle.Colors.cardBg()
        border = JBUI.Borders.empty(top, left, bottom, right)
    }

    override fun updateUI() {
        super.updateUI()
        isOpaque = false
        background = UiStyle.Colors.cardBg()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            val arc = UiStyle.Arc.component()
            g2.color = UiStyle.Colors.cardBg()
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            g2.color = UiStyle.Colors.cardBorder()
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
