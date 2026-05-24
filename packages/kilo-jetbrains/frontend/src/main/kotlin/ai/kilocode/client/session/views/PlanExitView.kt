package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.session.views.base.PartView
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JPanel

class PlanExitView(tool: Tool) : PartView() {
    companion object {
        fun canRender(tool: Tool): Boolean = tool.name == "plan_exit" && tool.state == ToolExecState.COMPLETED
    }

    override val contentId: String = tool.id

    private var item = tool

    private val title = JBLabel(KiloBundle.message("session.part.plan.ready"), AllIcons.Actions.Checked, JBLabel.LEFT)
    private val path = JBLabel().apply {
        foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
        setCopyable(true)
    }
    private val body = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val root = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = SessionUiStyle.View.surface()
        border = SessionUiStyle.View.card()
    }

    init {
        layout = BorderLayout()
        isOpaque = false
        body.border = JBUI.Borders.empty(
            JBUI.scale(SessionUiStyle.View.CARD_VERTICAL_PADDING),
            JBUI.scale(SessionUiStyle.View.CARD_HORIZONTAL_PADDING),
        )
        body.add(title)
        body.add(path)
        root.add(body, BorderLayout.CENTER)
        add(root, BorderLayout.CENTER)
        applyStyle(SessionEditorStyle.current())
        sync()
    }

    override fun update(content: Content) {
        if (content !is Tool) return
        item = content
        sync()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        title.font = style.boldEditorFont
        path.font = style.smallEditorFont
    }

    fun labelText(): String = listOf(title.text, path.text).filter { it.isNotBlank() }.joinToString(" ")

    private fun sync() {
        title.foreground = UiStyle.Colors.fg()
        val plan = plan(item)
        path.text = plan
        path.isVisible = plan.isNotBlank()
    }

    override fun dumpLabel() = "PlanExitView#$contentId(${labelText()})"
}

private fun plan(tool: Tool): String {
    tool.metadata["plan"]?.takeIf { it.isNotBlank() }?.let { return it }
    val out = tool.output ?: return ""
    return Regex("Plan is ready at (.+?)(?:\\. Ending planning turn\\.|$)")
        .find(out)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: ""
}
