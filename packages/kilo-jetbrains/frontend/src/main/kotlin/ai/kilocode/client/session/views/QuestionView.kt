package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.session.model.QuestionOption
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.QuestionReplyDto
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.AbstractButton
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Transcript-style question view — rendered inside [ai.kilocode.client.session.ui.SessionMessageListPanel]
 * at the end of the transcript when the session is in
 * [ai.kilocode.client.session.model.SessionState.AwaitingQuestion].
 *
 * Shows one [QuestionItem] at a time as a carousel with back/forward navigation.
 * Single-select items render as radio-button rows; multi-select as checkbox rows.
 * Each option shows a bold label and a regular description.
 */
class QuestionView(
    private val reply: (String, QuestionReplyDto) -> Unit,
    private val reject: (String) -> Unit,
    private val scroll: () -> Unit = {},
) : BorderLayoutPanel(), SessionEditorStyleTarget, SessionView {
    override val sessionViewKind = SessionView.Kind.Default

    private var requestId: String? = null
    private var question: Question? = null
    private var idx = 0
    private var selections = emptyList<MutableSet<String>>()
    private var style = SessionEditorStyle.current()

    init {
        isOpaque = false
        isVisible = false
    }

    /** Populate the view for [q] and make it visible, starting at the first question. */
    fun show(q: Question) {
        if (q.items.isEmpty()) {
            hideView()
            return
        }
        requestId = q.id
        question = q
        idx = 0
        selections = List(q.items.size) { mutableSetOf() }
        render()
        isVisible = true
        refresh()
    }

    /** Hide this view and clear all active request state. */
    fun hideView() {
        requestId = null
        question = null
        idx = 0
        selections = emptyList()
        removeAll()
        isVisible = false
        refresh()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        if (question == null) return
        render()
        refresh()
    }

    // ------ private rendering ------

    private fun render() {
        val q = question ?: return
        val item = q.items[idx]
        val total = q.items.size
        val set = selections[idx]

        removeAll()

        val card = BorderLayoutPanel()
        card.isOpaque = true
        card.background = SessionUiStyle.View.surface()
        card.border = SessionUiStyle.View.card()
        card.add(buildContent(item, total, set), BorderLayout.CENTER)
        add(card, BorderLayout.CENTER)
    }

    private fun buildContent(item: QuestionItem, total: Int, set: MutableSet<String>): JPanel {
        val root = JPanel()
        root.isOpaque = false
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = JBUI.Borders.empty(UiStyle.Gap.lg(), UiStyle.Gap.pad(), UiStyle.Gap.lg(), UiStyle.Gap.pad())

        val head = header(total)
        val body = body(item, set)
        val foot = footer(item, set)
        head.alignmentX = Component.LEFT_ALIGNMENT
        body.alignmentX = Component.LEFT_ALIGNMENT
        foot.alignmentX = Component.LEFT_ALIGNMENT
        root.add(head)
        root.add(body)
        root.add(foot)

        return root
    }

    // ── Header: summary + nav buttons ─────────────────────────────────────────

    private fun header(total: Int): JPanel {
        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.border = JBUI.Borders.emptyBottom(UiStyle.Gap.lg())

        val summary = JBLabel(KiloBundle.message("session.question.summary", idx + 1, total))
        summary.foreground = UiStyle.Colors.weak()
        row.add(summary, BorderLayout.WEST)

        if (total > 1) {
            row.add(navButtons(), BorderLayout.EAST)
        }

        return row
    }

    private fun navButtons(): JPanel {
        val nav = JPanel()
        nav.isOpaque = false
        nav.layout = BoxLayout(nav, BoxLayout.X_AXIS)

        val back = HoverIcon().apply {
            val ico = AllIcons.Actions.Back
            icon = ico
            disabledIcon = IconLoader.getDisabledIcon(ico)
            toolTipText = KiloBundle.message("session.question.back")
            isEnabled = idx > 0
            addActionListener { goBack() }
        }

        val fwd = HoverIcon().apply {
            val ico = AllIcons.Actions.Forward
            icon = ico
            disabledIcon = IconLoader.getDisabledIcon(ico)
            toolTipText = KiloBundle.message("session.question.next")
            val q = question
            isEnabled = q != null && idx < q.items.size - 1 && selections[idx].isNotEmpty()
            addActionListener { goForward() }
        }

        nav.add(back)
        nav.add(fwd)
        return nav
    }

    // ── Body: question text + hint + options ──────────────────────────────────

    private fun body(item: QuestionItem, set: MutableSet<String>): JPanel {
        val panel = JPanel()
        panel.isOpaque = false
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val title = text(item.question, UiStyle.Colors.fg(), true)
        title.border = JBUI.Borders.emptyBottom(UiStyle.Gap.xs())
        title.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(title)

        val hintKey = if (item.multiple) "session.question.hint.multi" else "session.question.hint.single"
        val hint = text(KiloBundle.message(hintKey), UiStyle.Colors.weak())
        hint.border = JBUI.Borders.emptyBottom(UiStyle.Gap.lg())
        hint.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(hint)

        val opts = optionList(item, set)
        opts.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(opts)

        return panel
    }

    private fun optionList(item: QuestionItem, set: MutableSet<String>): JPanel {
        val panel = JPanel()
        panel.isOpaque = false
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        if (item.multiple) {
            for (opt in item.options) {
                panel.add(checkboxRow(opt, set))
            }
        } else {
            val group = ButtonGroup()
            for (opt in item.options) {
                val row = radioRow(opt, set, group)
                panel.add(row)
            }
        }

        return panel
    }

    // ── Option rows ───────────────────────────────────────────────────────────

    private fun radioRow(opt: QuestionOption, set: MutableSet<String>, group: ButtonGroup): JPanel {
        val radio = JBRadioButton()
        radio.actionCommand = opt.label
        radio.isSelected = opt.label in set
        radio.isOpaque = false
        group.add(radio)

        radio.addActionListener {
            set.clear()
            set.add(opt.label)
            refreshSelection()
        }

        return optionRow(radio, opt)
    }

    private fun checkboxRow(opt: QuestionOption, set: MutableSet<String>): JPanel {
        val box = JBCheckBox()
        box.actionCommand = opt.label
        box.isSelected = opt.label in set
        box.isOpaque = false

        box.addActionListener {
            if (!set.remove(opt.label)) set.add(opt.label)
            refreshSelection()
        }

        return optionRow(box, opt)
    }

    private fun optionRow(toggle: AbstractButton, opt: QuestionOption): JPanel {
        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.border = JBUI.Borders.emptyBottom(UiStyle.Gap.lg())
        row.toolTipText = opt.description.ifBlank { null }
        row.alignmentX = Component.LEFT_ALIGNMENT

        val press = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (toggle.isEnabled) toggle.doClick()
            }
        }

        val icon = JPanel(BorderLayout())
        icon.isOpaque = false
        icon.border = JBUI.Borders.emptyRight(UiStyle.Gap.sm())
        icon.add(toggle, BorderLayout.NORTH)
        row.add(icon, BorderLayout.WEST)

        val col = JPanel()
        col.isOpaque = false
        col.layout = BoxLayout(col, BoxLayout.Y_AXIS)

        val label = text(opt.label, UiStyle.Colors.fg(), true)
        label.alignmentX = Component.LEFT_ALIGNMENT
        col.add(label)

        if (opt.description.isNotBlank()) {
            val desc = text(opt.description, UiStyle.Colors.weak())
            desc.alignmentX = Component.LEFT_ALIGNMENT
            desc.addMouseListener(press)
            col.add(desc)
        }

        row.add(col, BorderLayout.CENTER)

        // make clicking anywhere on the row trigger the toggle
        row.addMouseListener(press)
        icon.addMouseListener(press)
        col.addMouseListener(press)
        label.addMouseListener(press)

        return row
    }

    private fun text(value: String, color: Color, bold: Boolean = false): JBTextArea {
        return object : JBTextArea(value) {
            override fun getPreferredSize(): Dimension {
                val width = space()
                if (width <= 0) return super.getPreferredSize()
                val old = size
                setSize(width, Int.MAX_VALUE)
                val size = super.getPreferredSize()
                setSize(old)
                return Dimension(width, size.height)
            }

            override fun getMaximumSize(): Dimension {
                val size = preferredSize
                return Dimension(Int.MAX_VALUE, size.height)
            }

            private fun space(): Int {
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
            font = if (bold) style.boldEditorFont else style.transcriptFont
        }
    }

    // ── Footer: Dismiss + Next/Submit ─────────────────────────────────────────

    private fun footer(item: QuestionItem, set: MutableSet<String>): JPanel {
        val q = question ?: return JPanel()
        val last = idx == q.items.size - 1

        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.border = JBUI.Borders.emptyTop(UiStyle.Gap.lg())

        val dismiss = JButton(KiloBundle.message("session.question.dismiss"))
        dismiss.addActionListener { doReject() }
        row.add(dismiss, BorderLayout.WEST)

        val rightLabel = if (last) KiloBundle.message("session.question.submit")
                         else KiloBundle.message("session.question.next")
        val right = JButton(rightLabel)
        right.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, last)
        right.isEnabled = set.isNotEmpty()
        right.addActionListener {
            if (last) doReply()
            else goForward()
        }
        row.add(right, BorderLayout.EAST)

        return row
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun goBack() {
        if (idx <= 0) return
        idx--
        render()
        refresh()
        scroll()
    }

    private fun goForward() {
        val q = question ?: return
        if (idx >= q.items.size - 1) return
        if (selections[idx].isEmpty()) return
        idx++
        render()
        refresh()
        scroll()
    }

    private fun refreshSelection() {
        // Re-render is cheap and keeps state consistent; do a full render.
        render()
        refresh()
        scroll()
    }

    // ── Submit / reject ───────────────────────────────────────────────────────

    private fun doReply() {
        val id = requestId ?: return
        reply(id, QuestionReplyDto(selections.map { it.toList() }))
        hideView()
    }

    private fun doReject() {
        val id = requestId ?: return
        reject(id)
        hideView()
    }

    private fun refresh() {
        revalidate()
        repaint()
        parent?.revalidate()
        parent?.repaint()
    }
}
