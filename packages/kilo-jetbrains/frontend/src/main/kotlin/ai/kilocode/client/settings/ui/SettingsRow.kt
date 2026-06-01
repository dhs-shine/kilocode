package ai.kilocode.client.settings.ui

import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.HAlign
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.layout.StackAxis
import ai.kilocode.client.ui.layout.VAlign
import ai.kilocode.client.ui.layout.align
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SettingsRow(
    title: String,
    description: String? = null,
    value: JComponent,
) : JPanel(BorderLayout()) {

    init {
        border = JBUI.Borders.empty(UiStyle.Gap.pad(), 0, UiStyle.Gap.pad(), 0)

        val labels = Stack.vertical(UiStyle.Gap.sm())
        labels.next(JBLabel(title).apply { font = UiStyle.Fonts.bold() })
        if (description != null) {
            labels.next(JBLabel(description).apply {
                font = UiStyle.Fonts.hint()
                foreground = UIUtil.getContextHelpForeground()
            })
        }

        add(labels, BorderLayout.CENTER)
        add(value.align(HAlign.RIGHT, VAlign.CENTER), BorderLayout.EAST)
    }
}

class SettingsRows : Stack(StackAxis.VERTICAL) {
    private var rows = 0

    fun row(child: SettingsRow): SettingsRows {
        if (rows > 0) next(SeparatorComponent(0, JBColor.border(), null))
        next(child)
        rows += 1
        return this
    }

    override fun removeAll() {
        rows = 0
        super.removeAll()
    }
}
