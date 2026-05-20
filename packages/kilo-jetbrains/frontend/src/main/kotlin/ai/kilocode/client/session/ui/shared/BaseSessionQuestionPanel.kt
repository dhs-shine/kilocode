package ai.kilocode.client.session.ui.shared

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.RoundedContentPanel
import ai.kilocode.client.ui.UiStyle
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shared rounded background panel for session inline views that follow the
 * question-view visual style: a card surface with a header text area, a
 * description text area, an optional component above the header, and slots
 * for view-specific body and footer content.
 *
 * Both [ai.kilocode.client.session.views.question.QuestionView] and
 * [ai.kilocode.client.session.views.LoginRequiredView] use this as their
 * outer card shell so they share the same background, padding, and text
 * styling without duplicating the setup.
 */
class BaseSessionQuestionPanel : RoundedContentPanel(
    UiStyle.Gap.lg(),
    UiStyle.Gap.pad(),
), SessionEditorStyleTarget {

    private var style = SessionEditorStyle.current()

    // All JBTextArea instances that need editor-font updates, paired with bold flag
    private val tracked = mutableListOf<Pair<JBTextArea, Boolean>>()

    // ---- header text ----
    val headerText: JBTextArea = makeText("", UiStyle.Colors.fg(), bold = true)

    // ---- description text ----
    val descriptionText: JBTextArea = makeText("", UiStyle.Colors.weak(), bold = false)

    // ---- inner layout ----
    private val col = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    init {
        addToCenter(col)
    }

    /**
     * Optional panel rendered above the header row (e.g. summary + nav in
     * [ai.kilocode.client.session.views.question.QuestionView]).  When set,
     * it is inserted as the first child of the column; calling with `null`
     * removes a previously set component.
     *
     * The header/description text areas follow immediately after.
     */
    fun setTopPanel(top: JComponent?) {
        // Remove any existing top slot (first child if it is not header/desc)
        if (col.componentCount > 0 && col.getComponent(0) !== headerText) {
            col.remove(0)
        }
        col.removeAll()
        if (top != null) col.add(top)
        col.add(headerText)
        col.add(descriptionText)
    }

    /**
     * Replace the body slot that comes after the header/description.
     * Pass `null` to remove the current body.
     */
    fun setBody(body: JComponent?) {
        // Remove components after header+desc (index 0..1 or 0..2 with top)
        val fixed = if (col.componentCount > 0 && col.getComponent(0) !== headerText) 3 else 2
        while (col.componentCount > fixed) col.remove(fixed)
        if (body != null) col.add(body)
    }

    /**
     * Replace the footer slot that comes after the body.
     * Pass `null` to remove the current footer.
     */
    fun setFooter(footer: JComponent?) {
        val fixed = if (col.componentCount > 0 && col.getComponent(0) !== headerText) 3 else 2
        // footer is at fixed+1 if body exists, or at fixed if no body
        // simplest: remove anything beyond the header/desc/body block
        while (col.componentCount > fixed + 1) col.remove(fixed + 1)
        if (footer != null) col.add(footer)
    }

    // ---- SessionEditorStyleTarget ----

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        for ((area, bold) in tracked) applyFont(area, bold)
    }

    // ---- contentColor override ----

    override fun contentColor(): Color = SessionUiStyle.View.surface()

    override fun outlineColor(): Color = SessionUiStyle.View.line()

    // ---- helpers ----

    private fun makeText(value: String, color: Color, bold: Boolean): JBTextArea {
        val area = object : JBTextArea(value) {
            override fun getPreferredSize() = withWidth(super.getPreferredSize().height)

            override fun getMaximumSize(): Dimension {
                val size = preferredSize
                return Dimension(Int.MAX_VALUE, size.height)
            }

            private fun withWidth(fallback: Int): Dimension {
                val w = availableWidth()
                if (w <= 0) return Dimension(super.getPreferredSize().width, fallback)
                val old = size
                setSize(w, Int.MAX_VALUE)
                val ps = super.getPreferredSize()
                setSize(old)
                return Dimension(w, ps.height)
            }

            private fun availableWidth(): Int {
                var node = parent
                while (node != null) {
                    if (node.width > 0) {
                        val ins = node.insets
                        return (node.width - ins.left - ins.right).coerceAtLeast(0)
                    }
                    node = node.parent
                }
                return width
            }
        }.apply {
            isEditable = false
            isOpaque = false
            isFocusable = false
            caret.isVisible = false
            caret.isSelectionVisible = false
            lineWrap = true
            wrapStyleWord = true
            foreground = color
            border = JBUI.Borders.empty()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        tracked.add(area to bold)
        applyFont(area, bold)
        return area
    }

    private fun applyFont(area: JBTextArea, bold: Boolean) {
        val font = if (bold) style.boldEditorFont else style.transcriptFont
        if (area.font != font) area.font = font
    }
}
