package ai.kilocode.client.session.views.base

import ai.kilocode.client.session.ui.style.SessionUiStyle
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JComponent

abstract class PrimarySessionPartView(
    header: JComponent,
    content: JComponent,
    expanded: Boolean = false,
    expandable: Boolean = true,
) : AbstractSessionPartView(header, content, expanded, expandable) {
    init {
        isOpaque = true
        background = SessionUiStyle.View.surface()
        row.isOpaque = true
        row.background = SessionUiStyle.View.header()
        row.border = JBUI.Borders.empty(
            JBUI.scale(SessionUiStyle.View.SESSION_VIEW_VERTICAL_PADDING),
            JBUI.scale(SessionUiStyle.View.SESSION_VIEW_HORIZONTAL_PADDING),
        )
        syncBorder()
    }

    override fun expand(): Boolean {
        val changed = super.expand()
        if (changed) syncBorder()
        return changed
    }

    override fun collapse(): Boolean {
        val changed = super.collapse()
        if (changed) syncBorder()
        return changed
    }

    override fun hoverColor(value: Boolean) = if (value) SessionUiStyle.View.headerHover() else SessionUiStyle.View.header()

    override fun applyHover(value: Boolean, color: Color) {
        syncBorder()
        repaint()
    }

    private fun syncBorder() {
        border = if (isExpanded()) {
            val color = if (row.background?.rgb == SessionUiStyle.View.headerHover().rgb) {
                SessionUiStyle.View.hoverLine()
            } else {
                SessionUiStyle.View.line()
            }
            SessionUiStyle.View.sessionView(color)
        } else {
            JBUI.Borders.empty(1)
        }
    }
}
