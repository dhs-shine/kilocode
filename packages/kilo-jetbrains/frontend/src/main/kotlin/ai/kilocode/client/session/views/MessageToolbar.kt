package ai.kilocode.client.session.views

import ai.kilocode.client.session.ui.selection.SessionCopyButton
import ai.kilocode.client.ui.ToolbarButtonAction
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.toolbarButton
import com.intellij.icons.AllIcons
import ai.kilocode.client.plugin.KiloBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

internal class MessageToolbar(
    text: () -> String?,
    private val align: String = BorderLayout.LINE_END,
    actions: List<ToolbarButtonAction> = emptyList(),
    tooltip: String = KiloBundle.message("session.copy.hover"),
) : JPanel(BorderLayout()) {
    constructor(text: () -> String?, align: String, revert: (() -> Unit)?) : this(
        text,
        align,
        revert?.let {
            listOf(ToolbarButtonAction(AllIcons.Actions.Rollback, KiloBundle.message("revert.message.rollback"), it))
        }.orEmpty(),
        KiloBundle.message("session.copy.prompt"),
    )

    private val copy = SessionCopyButton(text = text, tooltip = tooltip)
    private val button = copy.button
    private val buttons = actions.map(::toolbarButton)
    private val row = Stack.horizontal(UiStyle.Gap.xs()).apply {
        buttons.forEach { next(it) }
        next(button)
    }

    init {
        isOpaque = false
        border = JBUI.Borders.emptyTop(UiStyle.Gap.xs())
        add(row, align)
    }

    @RequiresEdt
    fun sync(value: Boolean) {
        if (isVisible == value && button.isEnabled == value) return
        isVisible = value
        button.isEnabled = value
        buttons.forEach { it.isEnabled = value }
        revalidate()
        repaint()
    }

    @RequiresEdt
    fun setActive(value: Boolean) {
        sync(value)
    }

    @RequiresEdt
    fun active() = isVisible && button.isEnabled

    @RequiresEdt
    fun alignment() = align

    @RequiresEdt
    fun copyButton() = button

    fun placeholder(): JComponent = object : JPanel() {
        init {
            isOpaque = false
        }

        override fun getPreferredSize(): Dimension = this@MessageToolbar.preferredSize

        override fun getMinimumSize(): Dimension = this@MessageToolbar.minimumSize

        override fun getMaximumSize(): Dimension = this@MessageToolbar.maximumSize
    }

    override fun removeNotify() {
        copy.dismiss()
        super.removeNotify()
    }
}
