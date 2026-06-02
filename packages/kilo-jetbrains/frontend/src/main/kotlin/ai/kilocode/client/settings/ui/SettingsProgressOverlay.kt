package ai.kilocode.client.settings.ui

import ai.kilocode.client.ui.UiStyle
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

internal class SettingsProgressOverlay : JPanel(BorderLayout()) {
    private var label: JBLabel? = null

    init {
        val view = JBLabel()
        label = view
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Gap.lg(), UiStyle.Gap.pad(), UiStyle.Gap.lg(), UiStyle.Gap.pad())
        add(view, BorderLayout.CENTER)
        isVisible = false
        syncColors()
    }

    fun showProgress(text: String) {
        val view = requireNotNull(label)
        if (view.text != text) view.text = text
        if (!isVisible) isVisible = true
        revalidate()
        repaint()
    }

    fun clearProgress() {
        val view = requireNotNull(label)
        if (!isVisible && view.text.isNullOrBlank()) return
        view.text = ""
        isVisible = false
        revalidate()
        repaint()
    }

    override fun updateUI() {
        super.updateUI()
        syncColors()
    }

    private fun syncColors() {
        val hint = HintUtil.getInformationHint()
        background = hint.textBackground
        foreground = hint.textForeground
        label?.foreground = foreground
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            val arc = UiStyle.Arc.component()
            g2.fillRoundRect(0, 0, width, height, arc, arc)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
