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
 *
 * The column always contains (in order): optional top, [headerText],
 * [descriptionText], optional body, optional footer. Call [setTopPanel],
 * [setBody], or [setFooter] to replace those slots at any time.
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

    // ---- slot fields ----
    private var top: JComponent? = null
    private var body: JComponent? = null
    private var footer: JComponent? = null

    // ---- inner layout ----
    private val col = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    init {
        addToCenter(col)
        rebuildCol()
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
        this.top = top
        rebuildCol()
    }

    /**
     * Replace the body slot that comes after the header/description.
     * Pass `null` to remove the current body.
     */
    fun setBody(body: JComponent?) {
        this.body = body
        rebuildCol()
    }

    /**
     * Replace the footer slot that comes after the body.
     * Pass `null` to remove the current footer.
     */
    fun setFooter(footer: JComponent?) {
        this.footer = footer
        rebuildCol()
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

    private fun rebuildCol() {
        col.removeAll()
        top?.let { col.add(it) }
        col.add(headerText)
        col.add(descriptionText)
        body?.let { col.add(it) }
        footer?.let { col.add(it) }
        col.revalidate()
        col.repaint()
    }

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
