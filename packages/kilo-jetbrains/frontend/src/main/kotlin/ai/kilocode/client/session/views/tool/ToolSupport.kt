@file:Suppress("TooManyFunctions")

package ai.kilocode.client.session.views.tool

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.HAlign
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.layout.VAlign
import ai.kilocode.client.ui.layout.align
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class ToolParts(
    val header: JPanel,
    val glyph: JBLabel,
    val title: JBLabel,
    val sub: JBLabel,
    val link: JBLabel,
    val slot: JPanel,
    val state: JBLabel,
    val center: JPanel,
    val controls: JComponent,
    private val open: ((String) -> Unit)? = null,
    val extra: JBLabel? = null,
    val targets: List<JBLabel> = emptyList(),
) {
    var href: String? = null
    var label: String = ""
    private var body: ToolBody? = null

    val text: JBTextArea?
        get() = body?.text

    val scroll: JBScrollPane?
        get() = body?.scroll

    fun scroll(tool: Tool): JBScrollPane = body(tool).scroll

    fun bodyCreated() = body != null

    fun openLink() {
        val value = href ?: return
        open?.invoke(value)
    }

    private fun body(tool: Tool): ToolBody {
        val item = body
        if (item != null) return item
        val text = JBTextArea().apply {
            isEditable = false
            caret.isVisible = false
            caret.isSelectionVisible = true
            lineWrap = true
            wrapStyleWord = true
            foreground = if (tool.state == ToolExecState.ERROR) UiStyle.Colors.errorLabelForeground() else UiStyle.Colors.fg()
            background = SessionUiStyle.View.surface()
            border = JBUI.Borders.empty(
                JBUI.scale(SessionUiStyle.View.SESSION_VIEW_VERTICAL_PADDING),
                JBUI.scale(SessionUiStyle.View.SESSION_VIEW_HORIZONTAL_PADDING),
            )
        }
        val scroll = JBScrollPane(text).apply {
            border = SessionUiStyle.View.topOutline()
            isOpaque = true
            background = SessionUiStyle.View.surface()
            viewport.background = SessionUiStyle.View.surface()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        return ToolBody(text, scroll).also { body = it }
    }
}

class ToolBody(
    val text: JBTextArea,
    val scroll: JBScrollPane,
)

private const val SUB_CARD = "sub"
private const val LINK_CARD = "link"

internal fun toolParts(tool: Tool, openFile: ((String) -> Unit)? = null): ToolParts {
    lateinit var parts: ToolParts
    val glyph = JBLabel()
    val title = JBLabel()
    val sub = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    val link = JBLabel().apply {
        isVisible = false
        isFocusable = false
        foreground = UiStyle.Colors.fg()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        setRequestFocusEnabled(false)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                parts.openLink()
            }
        })
    }
    val slot = JPanel(CardLayout()).apply {
        isOpaque = false
        add(sub, SUB_CARD)
        add(link, LINK_CARD)
    }
    val state = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    val center = JPanel(BorderLayout(JBUI.scale(SessionUiStyle.View.SESSION_VIEW_GAP), 0)).apply { isOpaque = false }
    val controls = Stack.horizontal()
    val header = JPanel(BorderLayout(JBUI.scale(SessionUiStyle.View.SESSION_VIEW_GAP), 0)).apply {
        isOpaque = false
        center.add(title, BorderLayout.WEST)
        center.add(slot, BorderLayout.CENTER)
        add(glyph, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)
        add(controls, BorderLayout.EAST)
    }
    parts = ToolParts(header, glyph, title, sub, link, slot, state, center, controls, openFile)
    return parts.also {
        controls.add(it.state)
    }
}

internal fun searchParts(count: Int): ToolParts {
    val glyph = JBLabel()
    val title = JBLabel()
    val sub = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    val targets = List(count) {
        JBLabel().apply {
            foreground = UiStyle.Colors.weak()
            minimumSize = Dimension(0, minimumSize.height)
        }
    }
    val link = JBLabel().apply { isVisible = false }
    val slot = JPanel(CardLayout()).apply {
        isOpaque = false
        add(sub, SUB_CARD)
        add(link, LINK_CARD)
    }
    val state = JBLabel().apply { foreground = UiStyle.Colors.weak() }
    val stack = Stack.fitHorizontal(UiStyle.Gap.xs()).apply { targets.forEach { next(it) } }
    val target = stack.align(HAlign.TRACK, VAlign.CENTER)
    val center = JPanel(BorderLayout(JBUI.scale(SessionUiStyle.View.SESSION_VIEW_GAP), 0)).apply {
        isOpaque = false
        minimumSize = Dimension(0, minimumSize.height)
        add(title, BorderLayout.WEST)
        add(target, BorderLayout.CENTER)
    }
    val controls = Stack.horizontal()
    val header = JPanel(BorderLayout(JBUI.scale(SessionUiStyle.View.SESSION_VIEW_GAP), 0)).apply {
        isOpaque = false
        add(glyph, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)
        add(controls, BorderLayout.EAST)
    }
    return ToolParts(header, glyph, title, sub, link, slot, state, center, controls, targets = targets).also {
        controls.add(it.state)
    }
}

internal fun icon(tool: Tool) = when (tool.name) {
    "read" -> AllIcons.Actions.Preview
    "bash" -> AllIcons.Debugger.Console
    else -> when (tool.state) {
        ToolExecState.PENDING -> AllIcons.Process.Step_1
        ToolExecState.RUNNING -> AllIcons.Process.Step_2
        ToolExecState.COMPLETED -> AllIcons.Actions.Checked
        ToolExecState.ERROR -> AllIcons.General.Error
    }
}

internal fun title(tool: Tool) = when (tool.name) {
    "read" -> KiloBundle.message("session.part.tool.read")
    "bash" -> KiloBundle.message("session.part.tool.shell")
    else -> toolTitle(tool)
}

internal fun subtitle(tool: Tool) = when (tool.name) {
    "read" -> readPath(tool)
    "bash" -> shellTitle(tool)
    else -> toolSubtitle(tool)
}

internal fun setText(label: JBLabel, text: String): Boolean {
    val value = if (text.isBlank()) "" else XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(text))
    if (label.text == value) return false
    label.text = value
    return true
}

internal fun setPlainText(label: JBLabel, text: String): Boolean {
    if (label.text == text) return false
    label.text = text
    return true
}

internal fun setLinkText(parts: ToolParts, text: String): Boolean {
    val value = if (text.isBlank()) "" else XmlStringUtil.wrapInHtml("<u>${XmlStringUtil.escapeString(text)}</u>")
    if (parts.label == text && parts.link.text == value) return false
    parts.label = text
    parts.link.text = value
    return true
}

internal fun show(parts: ToolParts, link: Boolean): Boolean {
    if (parts.link.isVisible == link && parts.sub.isVisible != link) return false
    (parts.slot.layout as CardLayout).show(parts.slot, if (link) LINK_CARD else SUB_CARD)
    return true
}

internal fun subtitleText(parts: ToolParts): String = if (parts.link.isVisible) parts.label else parts.sub.text

internal fun setIcon(label: JBLabel, icon: Icon): Boolean {
    if (label.icon === icon) return false
    label.icon = icon
    return true
}

internal fun setVisible(component: JComponent, visible: Boolean): Boolean {
    if (component.isVisible == visible) return false
    component.isVisible = visible
    return true
}

internal fun setForeground(component: JComponent, color: Color): Boolean {
    if (same(component.foreground, color)) return false
    component.foreground = color
    return true
}

internal fun setFont(component: JComponent, font: Font): Boolean {
    if (component.font == font) return false
    component.font = font
    return true
}

private fun same(a: Color?, b: Color): Boolean = a?.rgb == b.rgb

internal fun color(tool: Tool) = when (tool.state) {
    ToolExecState.PENDING -> SessionUiStyle.View.Tool.pending()
    ToolExecState.RUNNING -> SessionUiStyle.View.Tool.running()
    ToolExecState.COMPLETED -> SessionUiStyle.View.Tool.completed()
    ToolExecState.ERROR -> SessionUiStyle.View.Tool.error()
}

internal fun titleColor(tool: Tool) = if (tool.state == ToolExecState.ERROR) {
    UiStyle.Colors.errorLabelForeground()
} else {
    UiStyle.Colors.fg()
}

internal fun stateText(tool: Tool) = when (tool.state) {
    ToolExecState.PENDING -> KiloBundle.message("session.part.tool.pending")
    ToolExecState.RUNNING -> KiloBundle.message("session.part.tool.running")
    ToolExecState.COMPLETED -> ""
    ToolExecState.ERROR -> KiloBundle.message("session.part.tool.error")
}

private fun readPath(tool: Tool): String {
    val target = target(tool)
    if (target != null) {
        if (target.type == "file") return tail(target.path).ifBlank { target.path }
        return target.path
    }
    val path = tool.input["filePath"] ?: tool.input["path"] ?: tool.title ?: return tool.name
    return tail(path).ifBlank { path }
}

internal fun globDirectory(tool: Tool): String =
    tool.input["path"]?.takeIf { it.isNotBlank() }
        ?: tool.title?.takeIf { it.isNotBlank() }
        ?: ""

internal fun globPattern(tool: Tool): String =
    tool.input["pattern"]?.takeIf { it.isNotBlank() }?.let { "pattern=$it" } ?: ""

internal fun searchTargets(tool: Tool): List<String> = listOfNotNull(
    tool.input["path"]?.takeIf { it.isNotBlank() },
    tool.input["pattern"]?.takeIf { it.isNotBlank() }?.let { "pattern=$it" },
    tool.input["include"]?.takeIf { it.isNotBlank() }?.let { "include=$it" },
)

internal data class Target(
    val path: String,
    val type: String,
)

internal fun target(tool: Tool): Target? {
    val out = output(tool)
    if (out.isBlank()) return null
    val path = tag(out, "path") ?: return null
    val type = tag(out, "type") ?: return null
    return Target(path, type.lowercase())
}

private fun tag(text: String, name: String): String? =
    Regex("<$name>\\s*([\\s\\S]*?)\\s*</$name>")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun shellTitle(tool: Tool): String =
    tool.input["description"]?.takeIf { it.isNotBlank() }
        ?: tool.metadata["description"]?.takeIf { it.isNotBlank() }
        ?: tool.title?.takeIf { it.isNotBlank() }
        ?: command(tool).lineSequence().firstOrNull { it.isNotBlank() }
        ?: ""

internal fun command(tool: Tool): String =
    tool.input["command"]?.takeIf { it.isNotBlank() }
        ?: tool.metadata["command"]?.takeIf { it.isNotBlank() }
        ?: ""

internal fun output(tool: Tool): String =
    tool.output?.takeIf { it.isNotBlank() }
        ?: tool.metadata["output"]?.takeIf { it.isNotBlank() }
        ?: ""

internal fun preview(tool: Tool): String = if (tool.name == "bash") shellPreview(tool) else plainPreview(tool)

internal fun body(tool: Tool): String = if (tool.name == "bash") shellBody(tool) else plainBody(tool)

private fun shellPreview(tool: Tool): String {
    val cmd = command(tool)
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return Preview().apply {
        if (cmd.isNotBlank()) append("$ ").append(cmd)
        if (out.isNotBlank()) {
            sep()
            append(out)
        }
        if (err != null) {
            sep()
            append(err)
        }
    }.build()
}

private fun shellBody(tool: Tool): String {
    val cmd = command(tool)
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return buildString {
        if (cmd.isNotBlank()) append("$ ").append(cmd)
        if (out.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(out)
        }
        if (err != null) {
            if (isNotEmpty()) append("\n\n")
            append(err)
        }
    }
}

private fun plainPreview(tool: Tool): String {
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return Preview().apply {
        if (out.isNotBlank()) append(out)
        if (err != null) {
            sep()
            append(err)
        }
    }.build()
}

internal fun plainBody(tool: Tool): String {
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return listOf(out, err).filter { !it.isNullOrBlank() }.joinToString("\n\n")
}

internal fun canExpand(tool: Tool): Boolean {
    if (tool.name == "bash") return command(tool).isNotBlank() || output(tool).isNotBlank() || !tool.error.isNullOrBlank()
    return output(tool).isNotBlank() || !tool.error.isNullOrBlank()
}

private fun toolTitle(tool: Tool): String =
    tool.title?.takeIf { it.isNotBlank() }
        ?: tool.name.replace('_', ' ').replaceFirstChar { it.titlecase() }

private fun toolSubtitle(tool: Tool): String {
    val base = listOf("description", "query", "url", "filePath", "path", "name")
        .mapNotNull { tool.input[it]?.takeIf { value -> value.isNotBlank() } }
        .firstOrNull()
    val args = listOf("pattern", "include", "offset", "limit")
        .mapNotNull { key -> tool.input[key]?.takeIf { it.isNotBlank() }?.let { "$key=$it" } }
    return listOfNotNull(base).plus(args).joinToString(" ")
}

internal fun tail(path: String): String {
    val value = path.trimEnd('/', '\\')
    val index = maxOf(value.lastIndexOf('/'), value.lastIndexOf('\\'))
    if (index < 0) return value
    return value.substring(index + 1)
}

private class Preview {
    private val text = StringBuilder()
    private var cut = false

    fun append(value: String): Preview {
        if (cut) return this
        val rem = SessionUiStyle.View.Tool.PREVIEW_LIMIT - text.length
        if (value.length <= rem) {
            text.append(value)
            return this
        }
        if (rem > 0) text.append(value, 0, rem)
        cut = true
        return this
    }

    fun sep(): Preview {
        if (text.isNotEmpty()) append("\n\n")
        return this
    }

    fun build(): String {
        if (!cut) return text.toString()
        if (text.isNotEmpty()) text.append("\n\n")
        text.append(KiloBundle.message("session.part.tool.truncated"))
        return text.toString()
    }
}
