package ai.kilocode.client.session.views

import ai.kilocode.client.session.ui.selection.SessionCopyButton
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JPanel

internal class MessageToolbar(
    private val align: String = BorderLayout.LINE_START,
    private val text: () -> String?,
) : JPanel(BorderLayout()) {
    private val copy = SessionCopyButton(text = text)
    private val button = copy.button
    private var paint = true

    init {
        isOpaque = false
        add(button, align)
    }

    @RequiresEdt
    fun sync(value: Boolean) {
        if (isVisible == value && paint == value && button.isEnabled == value) return
        isVisible = value
        paint = value
        button.isEnabled = value
        revalidate()
        repaint()
    }

    @RequiresEdt
    fun paint(value: Boolean) {
        if (!isVisible) isVisible = true
        if (paint == value && button.isEnabled == value) return
        paint = value
        button.isEnabled = value
        repaint()
    }

    @RequiresEdt
    fun paints() = paint

    @RequiresEdt
    fun alignment() = align

    @RequiresEdt
    fun copyButton() = button

    override fun removeNotify() {
        copy.dismiss()
        super.removeNotify()
    }

    override fun paintComponent(g: Graphics) {
        if (!paint) return
        super.paintComponent(g)
    }

    override fun paintChildren(g: Graphics) {
        if (!paint) return
        super.paintChildren(g)
    }
}
