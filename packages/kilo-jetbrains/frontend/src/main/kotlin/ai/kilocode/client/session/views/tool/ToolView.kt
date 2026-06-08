package ai.kilocode.client.session.views.tool

import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.ui.selection.SessionSelection
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.session.views.base.SecondarySessionPartView
import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.ScrollPaneConstants

/** Renders non-read tool calls with VS Code-inspired rows/cards. */
class ToolView(
    tool: Tool,
    private val selection: SessionSelection? = null,
    private val parts: ToolParts = toolParts(tool),
) : SecondarySessionPartView(parts.header, { parts.scroll(tool) }) {

    override val contentId: String = tool.id

    private var item = tool
    private var style = SessionEditorStyle.current()
    private var registered = false

    init {
        bindHeader(parts.glyph, parts.title, parts.sub, parts.state, parts.center, parts.controls, parts.slot)
        applyStyle(style)
        sync()
    }

    override fun expand(): Boolean {
        val changed = super.expand()
        if (!changed) return false
        syncBody()
        applyBodyStyle()
        return true
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        if (!bodyVisible()) return size
        val height = row.preferredSize.height + bodyMaxHeight()
        return Dimension(size.width, minOf(size.height, height))
    }

    override fun update(content: Content) {
        if (content !is Tool) return
        val was = item.name
        item = content
        var changed = false
        if (was != content.name || !canExpand(content)) changed = collapse() || changed
        changed = sync() || changed
        changed = syncBody() || changed
        if (changed) refresh()
    }

    fun labelText(): String = listOf(parts.title.text, subtitleText(parts), parts.state.text)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    fun commandText(): String = command(item)
    fun outputText(): String = output(item)
    fun bodyText(): String = body(item)
    internal fun previewText(): String = parts.text?.text ?: preview(item)
    fun hasToggle(): Boolean = arrow.isVisible
    internal fun bodyFont() = parts.text?.font ?: style.transcriptFont
    internal fun titleFont() = parts.title.font
    internal fun subtitleFont() = parts.sub.font
    internal fun stateFont() = parts.state.font
    internal fun bodyEditable() = parts.text?.isEditable ?: false
    internal fun bodyCaretVisible() = parts.text?.caret?.isVisible ?: false
    internal fun bodyVisible() = parts.scroll?.parent === this
    internal fun controlCount() = if (arrow.isVisible) 1 else 0
    internal fun horizontalPolicy() = parts.scroll?.horizontalScrollBarPolicy ?: ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    internal fun bodyWrap() = parts.text?.lineWrap ?: true
    internal fun bodyMaxRows() = SessionUiStyle.View.Tool.BODY_LINES
    internal fun bodyCreated() = parts.bodyCreated()

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        var changed = false
        changed = setFont(parts.title, style.boldEditorFont) || changed
        changed = setFont(parts.sub, style.smallEditorFont) || changed
        changed = setFont(parts.link, style.smallEditorFont) || changed
        changed = setFont(parts.state, style.smallEditorFont) || changed
        changed = applyBodyStyle() || changed
        if (changed) refresh()
    }

    private fun sync(): Boolean {
        val expand = canExpand(item)
        var changed = false
        changed = syncExpandable(expand) || changed
        changed = setVisible(parts.state, !expand) || changed
        changed = syncLabels() || changed
        val text = parts.text
        if (text != null) changed = setForeground(text, bodyColor()) || changed
        return changed
    }

    private fun syncLabels(): Boolean {
        var changed = false
        changed = setIcon(parts.glyph, icon(item)) || changed
        changed = setForeground(parts.glyph, color(item)) || changed
        changed = setText(parts.title, title(item)) || changed
        changed = setText(parts.sub, subtitle(item)) || changed
        changed = setForeground(parts.title, titleColor(item)) || changed
        changed = setText(parts.state, stateText(item)) || changed
        changed = setForeground(parts.state, color(item)) || changed
        return changed
    }

    private fun syncBody(): Boolean {
        var changed = false
        val text = parts.text ?: return false
        val value = preview(item)
        if (text.text != value) {
            text.text = value
            text.caretPosition = 0
            changed = true
        }
        changed = setForeground(text, bodyColor()) || changed
        return changed
    }

    private fun applyBodyStyle(): Boolean {
        val text = parts.text ?: return false
        if (!registered && selection != null && text.parent != null) {
            registered = true
            selection.register(text, this)
        }
        return setFont(text, style.transcriptFont)
    }

    private fun bodyColor() = if (item.state == ToolExecState.ERROR) UiStyle.Colors.errorLabelForeground() else UiStyle.Colors.fg()

    private fun bodyMaxHeight(): Int {
        val text = parts.text ?: return 0
        return text.getFontMetrics(text.font).height * bodyMaxRows() +
            JBUI.scale(SessionUiStyle.View.SESSION_VIEW_BODY_EXTRA_HEIGHT)
    }

    override fun dumpLabel() = "ToolView#$contentId(${labelText()})"
}
