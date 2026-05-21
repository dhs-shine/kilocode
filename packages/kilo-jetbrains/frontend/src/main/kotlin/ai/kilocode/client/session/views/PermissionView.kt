package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Permission
import ai.kilocode.client.session.model.PermissionRequestState
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.shared.BaseSessionQuestionPanel
import ai.kilocode.client.session.ui.shared.SessionQuestionButton
import ai.kilocode.client.session.ui.shared.applyButton
import ai.kilocode.client.session.ui.shared.dismissButton
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.md.MdView
import ai.kilocode.rpc.dto.PermissionReplyDto
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Transcript-style permission view — rendered inside [ai.kilocode.client.session.ui.SessionMessageListPanel]
 * at the end of the transcript when the session is in
 * [ai.kilocode.client.session.model.SessionState.AwaitingPermission].
 *
 * Shows a rich card with command/pattern/diff details and Run/Deny actions.
 */
class PermissionView(
    private val reply: (String, PermissionReplyDto) -> Unit,
) : BorderLayoutPanel(), SessionEditorStyleTarget, SessionView {
    override val sessionViewKind = SessionView.Kind.Default

    private var requestId: String? = null
    private var style = SessionEditorStyle.current()

    private val card = BaseSessionQuestionPanel()

    private val body = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val footer = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val actions = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val run = applyButton(KiloBundle.message("session.permission.run")) { decide("once") }
    private val deny = dismissButton(KiloBundle.message("session.permission.deny")) { decide("reject") }

    // Track command MdView instances for style updates
    private val cmdViews = mutableListOf<MdView>()
    private val cmdScrolls = mutableListOf<JBScrollPane>()

    init {
        isOpaque = false
        isVisible = false

        actions.add(deny)
        actions.add(Box.createHorizontalStrut(UiStyle.Gap.sm()))
        actions.add(run)
        footer.add(actions, BorderLayout.EAST)

        card.setHeaderIcon(AllIcons.General.Warning, KiloBundle.message("session.permission.title"))
        card.setBody(body)
        card.setFooter(footer)
        addToCenter(card)
    }

    /** Populate the view for [permission] and make it visible. */
    fun show(permission: Permission) {
        requestId = permission.id

        card.headerText.text = KiloBundle.message("session.permission.title")

        body.removeAll()
        cmdViews.clear()
        cmdScrolls.clear()

        val toolName = permission.name
        val cmd = permission.meta.command
        val command = cmd != null || toolName == "bash"

        card.descriptionText.text = ""
        card.descriptionText.isVisible = false

        if (command) {
            addCodeBlock(cmd ?: "")
        } else {
            addCodeBlock(patternText(toolName, permission.patterns))
        }

        val responding = permission.state == PermissionRequestState.RESPONDING || permission.state == PermissionRequestState.RESOLVED
        run.isEnabled = !responding
        deny.isEnabled = !responding

        isVisible = true
        refresh()
    }

    /** Hide this view and clear the active request id. */
    fun hideView() {
        requestId = null
        body.removeAll()
        cmdViews.clear()
        cmdScrolls.clear()
        isVisible = false
        refresh()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        card.applyStyle(style)
        for (md in cmdViews) {
            applyMd(md)
        }
        for (scroll in cmdScrolls) {
            applyScroll(scroll)
        }
    }

    private fun addCodeBlock(text: String) {
        val md = MdView.html().apply {
            applyMd(this)
            component.border = JBUI.Borders.empty()
            set(fencedBlock(text))
        }
        cmdViews.add(md)

        val scroll = object : JBScrollPane(md.component) {
            override fun getPreferredSize(): Dimension {
                val fm = getFontMetrics(style.transcriptFont)
                val cap = fm.height * SessionUiStyle.View.Permission.COMMAND_LINES + JBUI.scale(SessionUiStyle.View.CARD_BODY_EXTRA_HEIGHT)
                val ps = super.getPreferredSize()
                return Dimension(ps.width, minOf(ps.height, cap))
            }

            override fun getMaximumSize(): Dimension {
                val fm = getFontMetrics(style.transcriptFont)
                val cap = fm.height * SessionUiStyle.View.Permission.COMMAND_LINES + JBUI.scale(SessionUiStyle.View.CARD_BODY_EXTRA_HEIGHT)
                return Dimension(Int.MAX_VALUE, cap)
            }
        }.apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            applyScroll(this)
        }
        scroll.alignmentX = Component.LEFT_ALIGNMENT
        cmdScrolls.add(scroll)
        body.add(scroll)
    }

    private fun patternText(tool: String, patterns: List<String>): String {
        val lbl = toolLabel(tool)
        val filtered = patterns.filter { it != "*" }
        if (filtered.isEmpty()) {
            return KiloBundle.message("session.permission.no.details", lbl)
        }
        if (filtered.size == 1) {
            return "$lbl  ${filtered[0]}"
        }
        return buildString {
            appendLine(KiloBundle.message("session.permission.patterns", lbl))
            append(filtered.joinToString("\n"))
        }
    }

    private fun toolLabel(tool: String): String = when (tool) {
        "read" -> KiloBundle.message("session.permission.tool.read")
        "edit" -> KiloBundle.message("session.permission.tool.edit")
        "write" -> KiloBundle.message("session.permission.tool.write")
        "patch" -> KiloBundle.message("session.permission.tool.patch")
        "multiedit" -> KiloBundle.message("session.permission.tool.multiedit")
        "glob" -> KiloBundle.message("session.permission.tool.glob")
        "grep" -> KiloBundle.message("session.permission.tool.grep")
        "list" -> KiloBundle.message("session.permission.tool.list")
        "bash" -> KiloBundle.message("session.permission.tool.bash")
        "external_directory" -> KiloBundle.message("session.permission.tool.external_directory")
        "webfetch" -> KiloBundle.message("session.permission.tool.webfetch")
        "websearch" -> KiloBundle.message("session.permission.tool.websearch")
        "codesearch" -> KiloBundle.message("session.permission.tool.codesearch")
        "todoread" -> KiloBundle.message("session.permission.tool.todoread")
        "todowrite" -> KiloBundle.message("session.permission.tool.todowrite")
        "task" -> KiloBundle.message("session.permission.tool.task")
        "skill" -> KiloBundle.message("session.permission.tool.skill")
        "lsp" -> KiloBundle.message("session.permission.tool.lsp")
        else -> tool
    }

    private fun decide(value: String) {
        val id = requestId ?: return
        run.isEnabled = false
        deny.isEnabled = false
        reply(id, PermissionReplyDto(reply = value))
    }

    private fun applyMd(md: MdView) {
        val bg = codeBackground()
        md.opaque = true
        md.font = style.transcriptFont
        md.foreground = style.editorForeground
        md.background = bg
        md.preBg = bg
        md.codeBg = bg
        md.preFg = style.editorForeground
        md.codeFont = style.editorFamily
        md.component.background = bg
    }

    private fun applyScroll(scroll: JBScrollPane) {
        val bg = codeBackground()
        scroll.border = JBUI.Borders.empty()
        scroll.background = bg
        scroll.viewport.background = bg
    }

    private fun codeBackground() = SessionUiStyle.View.headerHover()

    private fun refresh() {
        revalidate()
        repaint()
        parent?.revalidate()
        parent?.repaint()
    }

    // Test helpers
    internal fun runButtonForTest() = run
    internal fun denyButtonForTest() = deny
    internal fun firstCmdViewForTest() = cmdViews.firstOrNull()
    internal fun headerFontForTest() = card.headerText.font
}

/**
 * Wrap [cmd] in a fenced Markdown code block. The fence uses at least 3 backticks,
 * and is extended to be longer than any contiguous run of backticks inside [cmd]
 * so the fence cannot be broken by content.
 */
private fun fencedBlock(cmd: String): String {
    var max = 2
    var run = 0
    for (ch in cmd) {
        run = if (ch == '`') run + 1 else 0
        if (run > max) max = run
    }
    val fence = "`".repeat(max + 1)
    return "$fence\n$cmd\n$fence"
}
