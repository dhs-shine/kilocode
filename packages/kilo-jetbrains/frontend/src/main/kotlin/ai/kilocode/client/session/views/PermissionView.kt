package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Permission
import ai.kilocode.client.session.model.PermissionFileDiff
import ai.kilocode.client.session.model.PermissionRequestState
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.PermissionReplyDto
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
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

    private val card = BorderLayoutPanel()
    private val header = JBLabel()
    private val details = JPanel()
    private val actions = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), 0))
    private val run = JButton(KiloBundle.message("session.permission.run"))
    private val deny = JButton(KiloBundle.message("session.permission.deny"))

    // Track text areas so we can update their font on style change
    private val textAreas = mutableListOf<JBTextArea>()

    init {
        isVisible = false

        card.background = SessionUiStyle.View.surface()
        card.border = JBUI.Borders.compound(
            SessionUiStyle.View.card(),
            JBUI.Borders.empty(
                UiStyle.Gap.sm(),
                UiStyle.Gap.pad(),
                UiStyle.Gap.sm(),
                UiStyle.Gap.pad(),
            ),
        )

        val top = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.xs(), 0)).apply {
            isOpaque = false
            val icon = JBLabel(AllIcons.General.Warning)
            add(icon)
            add(Box.createHorizontalStrut(UiStyle.Gap.xs()))
            header.font = style.boldUiFont
            add(header)
        }

        details.layout = BoxLayout(details, BoxLayout.Y_AXIS)
        details.isOpaque = false

        actions.isOpaque = false
        actions.add(run)
        actions.add(deny)

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(top)
            add(Box.createVerticalStrut(UiStyle.Gap.sm()))
            add(details)
            add(Box.createVerticalStrut(UiStyle.Gap.sm()))
            add(actions)
        }

        card.add(body, BorderLayout.CENTER)
        add(card, BorderLayout.CENTER)

        run.addActionListener { decide("once") }
        deny.addActionListener { decide("reject") }
    }

    /** Populate the view for [permission] and make it visible. */
    fun show(permission: Permission) {
        requestId = permission.id

        header.text = KiloBundle.message("session.permission.title")
        header.font = style.boldUiFont

        details.removeAll()
        textAreas.clear()

        val toolName = permission.name
        val cmd = permission.meta.command
        if (cmd != null || toolName == "bash") {
            addCommandBlock(cmd ?: "")
        } else {
            addPatternBlock(toolName, permission.patterns)
        }

        val msg = permission.message
        if (!msg.isNullOrBlank()) {
            val msgLabel = JBLabel(msg).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = style.smallUiFont
            }
            details.add(Box.createVerticalStrut(UiStyle.Gap.xs()))
            details.add(msgLabel)
        }

        val diffs = permission.meta.fileDiffs
        if (diffs.isNotEmpty()) {
            details.add(Box.createVerticalStrut(UiStyle.Gap.sm()))
            val diffTitle = JBLabel(KiloBundle.message("session.permission.diff")).apply {
                font = style.boldUiFont
            }
            details.add(diffTitle)
            for (diff in diffs) {
                details.add(Box.createVerticalStrut(UiStyle.Gap.xs()))
                addDiffBlock(diff)
            }
        } else {
            val fallbackDiff = permission.meta.diff
            val fallbackPath = permission.meta.filePath
            if (fallbackDiff != null) {
                details.add(Box.createVerticalStrut(UiStyle.Gap.sm()))
                val diffTitle = JBLabel(KiloBundle.message("session.permission.diff")).apply {
                    font = style.boldUiFont
                }
                details.add(diffTitle)
                details.add(Box.createVerticalStrut(UiStyle.Gap.xs()))
                addDiffBlock(PermissionFileDiff(file = fallbackPath ?: "patch", patch = fallbackDiff, additions = 0, deletions = 0))
            }
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
        details.removeAll()
        textAreas.clear()
        isVisible = false
        refresh()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        header.font = style.boldUiFont
        for (area in textAreas) {
            area.font = style.transcriptFont
            area.background = style.editorScheme.defaultBackground
        }
    }

    private fun addCommandBlock(cmd: String) {
        val label = JBLabel(KiloBundle.message("session.permission.command")).apply {
            font = style.boldUiFont
        }
        details.add(label)
        details.add(Box.createVerticalStrut(UiStyle.Gap.xs()))
        val area = JBTextArea(cmd).apply {
            isEditable = false
            lineWrap = false
            font = style.transcriptFont
            background = style.editorScheme.defaultBackground
            foreground = UIUtil.getLabelForeground()
        }
        textAreas.add(area)
        val scroll = JBScrollPane(area).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            border = JBUI.Borders.customLine(SessionUiStyle.View.line(), 1)
        }
        details.add(scroll)
    }

    private fun addPatternBlock(tool: String, patterns: List<String>) {
        val lbl = toolLabel(tool)
        val filtered = patterns.filter { it != "*" }
        if (filtered.isEmpty()) {
            val noDetails = JBLabel(KiloBundle.message("session.permission.no.details", lbl)).apply {
                font = style.uiFont
            }
            details.add(noDetails)
            return
        }
        if (filtered.size == 1) {
            val row = JBLabel("$lbl  ${filtered[0]}").apply {
                font = style.uiFont
            }
            details.add(row)
            return
        }
        val title = JBLabel(KiloBundle.message("session.permission.patterns", lbl)).apply {
            font = style.boldUiFont
        }
        details.add(title)
        for (p in filtered) {
            details.add(Box.createVerticalStrut(UiStyle.Gap.xs()))
            val pathLabel = JBLabel(p).apply {
                font = style.transcriptFont
                foreground = UIUtil.getContextHelpForeground()
            }
            details.add(pathLabel)
        }
    }

    private fun addDiffBlock(diff: PermissionFileDiff) {
        val fileRow = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.xs(), 0)).apply {
            isOpaque = false
            val fileLabel = JBLabel(diff.file).apply {
                font = style.transcriptFont
            }
            add(fileLabel)
            if (diff.additions > 0 || diff.deletions > 0) {
                val summary = JBLabel(KiloBundle.message("session.permission.diff.summary", diff.additions, diff.deletions)).apply {
                    font = style.smallUiFont
                    foreground = UIUtil.getContextHelpForeground()
                }
                add(summary)
            }
        }
        details.add(fileRow)
        val patch = diff.patch
        if (!patch.isNullOrBlank()) {
            details.add(Box.createVerticalStrut(UiStyle.Gap.xs()))
            val area = JBTextArea(patch).apply {
                isEditable = false
                lineWrap = false
                font = style.transcriptFont
                background = style.editorScheme.defaultBackground
                foreground = UIUtil.getLabelForeground()
            }
            textAreas.add(area)
            val scroll = JBScrollPane(area).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                border = JBUI.Borders.customLine(SessionUiStyle.View.line(), 1)
            }
            details.add(scroll)
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

    private fun refresh() {
        revalidate()
        repaint()
        parent?.revalidate()
        parent?.repaint()
    }

    // Test helpers
    internal fun runButtonForTest() = run
    internal fun denyButtonForTest() = deny
}
