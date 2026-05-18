package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

private data class QuestionResult(
    val questions: List<String>,
    val answers: List<List<String>>,
)

private val json = Json { ignoreUnknownKeys = true }

private fun parseQuestions(raw: String): List<String>? {
    val arr = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return null
    val list = arr.mapNotNull { elem ->
        elem.jsonObject["question"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    return list.takeIf { it.isNotEmpty() }
}

private fun parseAnswers(raw: String?): List<List<String>> {
    if (raw.isNullOrBlank()) return emptyList()
    val arr = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
    return arr.map { elem ->
        runCatching {
            elem.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }
}

private fun parse(tool: Tool): QuestionResult? {
    if (tool.name != "question") return null
    if (tool.state != ToolExecState.COMPLETED) return null
    val raw = tool.input["questions"] ?: return null
    val questions = parseQuestions(raw) ?: return null
    val answers = parseAnswers(tool.metadata["answers"])
    return QuestionResult(questions, answers)
}

/**
 * Renders completed `question` tool parts as a structured answered-question card.
 *
 * Shows each question with its selected answer(s). Hides raw output text.
 * Falls back to [ToolView] when structured data cannot be parsed.
 */
class QuestionResultView(tool: Tool) : PartView() {

    override val contentId: String = tool.id

    private var result = parse(tool) ?: QuestionResult(emptyList(), emptyList())
    private var style = SessionEditorStyle.current()

    private val root = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = SessionUiStyle.View.surface()
        border = SessionUiStyle.View.card()
    }
    private val header = JPanel(BorderLayout(JBUI.scale(SessionUiStyle.View.CARD_LAYOUT_GAP), 0)).apply {
        isOpaque = true
        background = SessionUiStyle.View.header()
        border = JBUI.Borders.empty(
            JBUI.scale(SessionUiStyle.View.CARD_VERTICAL_PADDING),
            JBUI.scale(SessionUiStyle.View.CARD_HORIZONTAL_PADDING),
        )
    }
    private val glyph = JBLabel(AllIcons.General.Balloon)
    private val title = JBLabel()
    private val sub = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    private val arrow = JBLabel()
    private val center = JPanel(BorderLayout(JBUI.scale(SessionUiStyle.View.CARD_LAYOUT_GAP), 0)).apply {
        isOpaque = false
    }
    private val body = JPanel().apply {
        isOpaque = true
        background = SessionUiStyle.View.surface()
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(
            JBUI.scale(SessionUiStyle.View.CARD_VERTICAL_PADDING),
            JBUI.scale(SessionUiStyle.View.CARD_HORIZONTAL_PADDING),
        )
    }

    // Text components tracked for font updates
    private val textComponents = mutableListOf<JBTextArea>()

    private val click = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) { toggle() }
    }

    private val mouse = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) { setHover(true) }
        override fun mouseExited(e: MouseEvent) {
            if (inside(e)) return
            setHover(false)
        }
    }

    init {
        layout = BorderLayout()
        isOpaque = false

        center.add(title, BorderLayout.WEST)
        center.add(sub, BorderLayout.CENTER)
        header.add(glyph, BorderLayout.WEST)
        header.add(center, BorderLayout.CENTER)
        header.add(arrow, BorderLayout.EAST)
        root.add(header, BorderLayout.NORTH)

        listOf(header, glyph, title, sub, arrow, center).forEach {
            it.addMouseListener(click)
            it.addMouseListener(mouse)
            it.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        applyStyle(SessionEditorStyle.current())
        add(root, BorderLayout.CENTER)
        syncLabels()
        rebuildBody()
        // Default expanded: add body
        root.add(body, BorderLayout.CENTER)
        syncArrow()
    }

    override fun update(content: Content) {
        if (content !is Tool) return
        val parsed = parse(content)
        result = parsed ?: QuestionResult(emptyList(), emptyList())
        syncLabels()
        rebuildBody()
        refresh()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        setFont(title, style.boldEditorFont)
        setFont(sub, style.smallEditorFont)
        for (ta in textComponents) {
            val bold = ta.font?.isBold == true
            ta.font = if (bold) style.boldEditorFont else style.transcriptFont
        }
        refresh()
    }

    fun toggle() {
        val expanded = isExpanded()
        if (expanded) root.remove(body) else root.add(body, BorderLayout.CENTER)
        syncArrow()
        refresh()
    }

    fun isExpanded(): Boolean = body.parent === root

    /** Label text for test assertions — title + sub joined. */
    fun labelText(): String = listOf(title.text, sub.text).filter { it.isNotBlank() }.joinToString(" ")

    /** Body text for test assertions — all text areas joined. */
    fun bodyText(): String = textComponents.joinToString("\n") { it.text }

    override fun dumpLabel(): String = "QuestionResultView#$contentId(${labelText()})"

    companion object {
        fun canRender(tool: Tool): Boolean = parse(tool) != null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun syncLabels() {
        title.text = KiloBundle.message("session.question.result.title")
        val count = result.answers.count { it.isNotEmpty() }
        sub.text = KiloBundle.message("session.question.result.answered", count)
    }

    private fun rebuildBody() {
        body.removeAll()
        textComponents.clear()

        for ((i, q) in result.questions.withIndex()) {
            val row = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            if (i > 0) {
                row.border = JBUI.Borders.emptyTop(UiStyle.Gap.lg())
            }

            val qText = makeText(q, UiStyle.Colors.weak(), false)
            qText.alignmentX = Component.LEFT_ALIGNMENT
            row.add(qText)

            val joined = result.answers.getOrNull(i)?.joinToString(", ").orEmpty()
            val aText = makeText(
                joined.ifBlank { KiloBundle.message("session.question.review.notAnswered") },
                UiStyle.Colors.fg(),
                true,
            )
            aText.alignmentX = Component.LEFT_ALIGNMENT
            row.add(aText)

            body.add(row)
        }
    }

    private fun makeText(value: String, color: Color, bold: Boolean): JBTextArea {
        val ta = object : JBTextArea(value) {
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
        textComponents.add(ta)
        return ta
    }

    private fun syncArrow() {
        arrow.icon = if (isExpanded()) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
    }

    private fun setHover(value: Boolean) {
        val color = if (value) SessionUiStyle.View.headerHover() else SessionUiStyle.View.header()
        if (header.background?.rgb == color.rgb) return
        header.background = color
        header.repaint()
    }

    private fun inside(e: MouseEvent): Boolean {
        val point = SwingUtilities.convertPoint(e.component, e.point, header)
        return header.contains(point)
    }

    private fun setFont(label: JBLabel, font: Font): Boolean {
        if (label.font == font) return false
        label.font = font
        return true
    }

    private fun refresh() {
        revalidate()
        repaint()
    }
}
