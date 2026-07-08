package ai.kilocode.client.session.views

import ai.kilocode.client.session.ui.selection.SessionCopyButton
import ai.kilocode.client.ui.HoverIcon
import com.intellij.icons.AllIcons
import ai.kilocode.client.plugin.KiloBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JPanel

internal class MessageToolbar(
    private val text: () -> String?,
    private val align: String = BorderLayout.LINE_START,
    private val revert: (() -> Unit)? = null,
) : JPanel(BorderLayout()) {
    constructor(text: () -> String?) : this(text, BorderLayout.LINE_START, null)

    private val copy = SessionCopyButton(text = text)
    private val button = copy.button
    private val rollback = HoverIcon().apply {
        icon = AllIcons.Actions.Back
        toolTipText = KiloBundle.message("revert.message.rollback")
        accessibleContext.accessibleName = KiloBundle.message("revert.message.rollback")
        addActionListener { revert?.invoke() }
    }
    private val row = JPanel(BorderLayout()).apply {
        isOpaque = false
        if (revert != null) add(rollback, BorderLayout.LINE_START)
        add(button, BorderLayout.LINE_END)
    }

    init {
        isOpaque = false
        add(if (revert == null) button else row, align)
    }

    @RequiresEdt
    fun sync(value: Boolean) {
        if (isVisible == value && button.isEnabled == value) return
        isVisible = value
        button.isEnabled = value
        rollback.isEnabled = value
        revalidate()
        repaint()
    }

    @RequiresEdt
    fun paint(value: Boolean) {
        // Prompt toolbars stay visible to reserve layout space while their button is visually hidden.
        if (!isVisible) isVisible = true
        if (button.isEnabled == value) return
        button.isEnabled = value
        rollback.isEnabled = value
        repaint()
    }

    @RequiresEdt
    fun paints() = button.isEnabled

    @RequiresEdt
    fun alignment() = align

    @RequiresEdt
    fun copyButton() = button

    override fun removeNotify() {
        copy.dismiss()
        super.removeNotify()
    }

    override fun paintComponent(g: Graphics) {
        if (!button.isEnabled) return
        super.paintComponent(g)
    }

    override fun paintChildren(g: Graphics) {
        if (!button.isEnabled) return
        super.paintChildren(g)
    }
}
