package ai.kilocode.client.session.views.base

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.RoundedContentPanel
import ai.kilocode.client.ui.UiStyle
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
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
 * The column always contains (in order): optional top, header row with [headerText],
 * [descriptionText], optional body, optional footer. Call [setTopPanel],
 * [setHeaderIcon], [setBody], or [setFooter] to replace those slots at any time.
 */
class BaseQuestionView : RoundedContentPanel(
    UiStyle.Gap.lg(),
    UiStyle.Gap.pad(),
), SessionEditorStyleTarget {

    private var style = SessionEditorStyle.current()

    // All JBTextArea instances that need style updates, paired with bold flag
    private val tracked = mutableListOf<Pair<JBTextArea, Boolean>>()

    // ---- header text ----
    val headerText: JBTextArea = makeText("", UiStyle.Colors.fg(), bold = true)

    // ---- description text ----
    val descriptionText: JBTextArea = makeText("", UiStyle.Colors.weak(), bold = false).apply {
        border = JBUI.Borders.emptyTop(UiStyle.Gap.sm())
    }

    private val icon = JBLabel().apply {
        border = JBUI.Borders.emptyRight(UiStyle.Gap.sm())
        isVisible = false
    }

    private val header = object : JPanel(BorderLayout(UiStyle.Gap.sm(), 0)) {
        override fun getMaximumSize(): Dimension {
            val size = preferredSize
            return Dimension(Int.MAX_VALUE, size.height)
        }
    }.apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(icon, BorderLayout.WEST)
        add(headerText, BorderLayout.CENTER)
    }

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
    @RequiresEdt
    fun setTopPanel(top: JComponent?) {
        this.top = top
        rebuildCol()
    }

    /**
     * Optional icon rendered at the left edge of the header row.
     * Pass `null` to remove the icon while keeping header text alignment stable.
     */
    @RequiresEdt
    fun setHeaderIcon(icon: Icon?, tooltip: String? = null) {
        this.icon.icon = icon
        this.icon.toolTipText = tooltip
        this.icon.isVisible = icon != null
        this.icon.revalidate()
        this.icon.repaint()
    }

    /**
     * Replace the body slot that comes after the header/description.
     * Pass `null` to remove the current body.
     */
    @RequiresEdt
    fun setBody(body: JComponent?) {
        this.body = body
        rebuildCol()
    }

    /**
     * Replace the footer slot that comes after the body.
     * Pass `null` to remove the current footer.
     */
    @RequiresEdt
    fun setFooter(footer: JComponent?) {
        this.footer = footer
        rebuildCol()
    }

    // ---- SessionEditorStyleTarget ----

    @RequiresEdt
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
        col.add(header)
        col.add(descriptionText)
        body?.let {
            col.add(gap())
            col.add(it)
        }
        footer?.let {
            col.add(gap())
            col.add(it)
        }
        col.revalidate()
        col.repaint()
    }

    private fun gap(): Component = Box.createVerticalStrut(UiStyle.Gap.lg()).apply {
        setAlignmentX(Component.LEFT_ALIGNMENT)
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
        val base = if (bold) style.boldUiFont else style.uiFont
        val font = larger(base)
        if (area.font != font) area.font = font
    }

    private fun larger(font: Font): Font = font.deriveFont((font.size + 1).toFloat())
}

/**
 * A [javax.swing.JButton] variant used inside session question/login-required panels.
 *
 * Primary buttons receive [com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY] so they use the
 * platform's default-button accent. Buttons keep the standard Look-and-Feel
 * border, padding, disabled state, and focus painting, while their component
 * background follows the question card surface so border/focus chrome blends
 * into the inline panel instead of the surrounding transcript.
 */
class SessionQuestionButton(text: String, val primary: Boolean) : JButton(text) {

    init {
        if (primary) {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
        syncBackground()
    }

    override fun updateUI() {
        super.updateUI()
        syncBackground()
    }

    private fun syncBackground() {
        background = SessionUiStyle.View.surface()
    }
}

/** Create a non-primary (secondary) session question button. */
fun dismissButton(text: String, action: () -> Unit): SessionQuestionButton =
    SessionQuestionButton(text, primary = false).apply { addActionListener { action() } }

/** Create a primary (default/accent) session question button. */
fun applyButton(text: String, action: () -> Unit): SessionQuestionButton =
    SessionQuestionButton(text, primary = true).apply { addActionListener { action() } }
