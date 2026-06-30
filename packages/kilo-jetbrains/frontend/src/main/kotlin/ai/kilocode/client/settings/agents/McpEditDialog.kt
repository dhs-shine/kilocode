package ai.kilocode.client.settings.agents

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.BaseContentPanel
import ai.kilocode.client.settings.base.SettingsRow
import ai.kilocode.client.settings.base.SettingsRows
import ai.kilocode.client.settings.base.SettingsStackedRow
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.rpc.dto.McpConfigDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants

internal interface McpEditDialogHandle {
    fun showAndGet(): Boolean
    fun result(): McpConfigDto
}

internal class McpEditDialog(
    private val id: String,
    private val cfg: McpConfigDto,
) : DialogWrapper(true), McpEditDialogHandle {
    private val type = cfg.type ?: if (cfg.url.isNullOrBlank()) LOCAL else REMOTE
    private val command = JBTextField(cfg.command?.firstOrNull().orEmpty()).apply {
        columns = FIELD_COLUMNS
        emptyText.text = KiloBundle.message("settings.agentBehavior.mcp.edit.command.placeholder")
    }
    private val args = JBTextArea(cfg.command.orEmpty().drop(1).joinToString("\n")).apply {
        rows = 4
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty()
        emptyText.text = KiloBundle.message("settings.agentBehavior.mcp.edit.args.placeholder")
    }
    private val url = JBTextField(cfg.url.orEmpty()).apply {
        columns = FIELD_COLUMNS
        emptyText.text = KiloBundle.message("settings.agentBehavior.mcp.edit.url.placeholder")
    }
    private val env = linkedMapOf<String, String>().apply { putAll(cfg.environment.orEmpty()) }
    private val key = JBTextField().apply {
        columns = ENV_COLUMNS
        emptyText.text = KiloBundle.message("settings.agentBehavior.mcp.edit.env.key")
    }
    private val value = JBTextField().apply {
        columns = ENV_COLUMNS
        emptyText.text = KiloBundle.message("settings.agentBehavior.mcp.edit.env.value")
    }
    private val rows = SettingsRows()
    private var center: JComponent? = null

    init {
        title = KiloBundle.message("settings.agentBehavior.mcp.edit.title", id)
        init()
    }

    internal fun contentForTest(): JComponent = center ?: error("center panel not built")

    override fun result(): McpConfigDto {
        if (type == REMOTE) return cfg.copy(url = text(url.text))
        return cfg.copy(
            command = listOf(command.text.trim()) + args.text.lines().map { it.trim() }.filter { it.isNotEmpty() },
            environment = env.toMap(),
        )
    }

    override fun createCenterPanel(): JComponent {
        val panel = BaseContentPanel().apply {
            border = JBUI.Borders.empty(
                UiStyle.Gap.pad(),
                UiStyle.Gap.pad(),
                UiStyle.Gap.pad(),
                UiStyle.Gap.pad() * 2,
            )
        }
        SettingsRows().apply {
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.mcp.edit.transport"),
                transport(),
                JBLabel(id),
            ))
            panel.next(this)
        }
        if (type == REMOTE) {
            SettingsRows().apply {
                row(SettingsStackedRow(
                    KiloBundle.message("settings.agentBehavior.mcp.edit.url"),
                    null,
                    url,
                ))
                panel.next(this)
            }
        } else {
            SettingsRows().apply {
                row(SettingsStackedRow(
                    KiloBundle.message("settings.agentBehavior.mcp.edit.command"),
                    null,
                    command,
                ))
                row(SettingsStackedRow(
                    KiloBundle.message("settings.agentBehavior.mcp.edit.args"),
                    KiloBundle.message("settings.agentBehavior.mcp.edit.args.help"),
                    scroll(args),
                ))
                panel.next(this)
            }
            panel.section(
                KiloBundle.message("settings.agentBehavior.mcp.edit.env"),
                KiloBundle.message("settings.agentBehavior.mcp.edit.env.help"),
            ).row(envEditor())
        }
        val content = ScrollContent().apply {
            add(Stack.vertical().next(panel), BorderLayout.CENTER)
        }
        return JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }.also { center = it }
    }

    override fun getPreferredFocusedComponent(): JComponent = if (type == REMOTE) url else command

    override fun getDimensionServiceKey(): String = "Kilo.McpEditDialog"

    private fun envEditor(): JComponent = Stack.vertical(UiStyle.Gap.sm())
        .next(Stack.horizontal(UiStyle.Gap.sm())
            .next(key)
            .next(value)
            .next(JButton(KiloBundle.message("settings.agentBehavior.mcp.edit.env.add")).apply {
                addActionListener { addEnv() }
            }))
        .next(rows)
        .also { syncEnv() }

    private fun addEnv() {
        val name = key.text.trim()
        if (name.isBlank()) return
        env[name] = value.text.trim()
        key.text = ""
        value.text = ""
        syncEnv()
    }

    private fun syncEnv() {
        for ((name, item) in env) {
            val label = "$name=$item"
            val remove = HoverIcon().apply {
                icon = AllIcons.Actions.Close
                val text = KiloBundle.message("settings.agentBehavior.mcp.edit.env.remove", name)
                toolTipText = text
                accessibleContext.accessibleName = text
                addActionListener {
                    env.remove(name)
                    syncEnv()
                }
            }
            if (rows.update(name, label, value = remove) == null) rows.row(name, SettingsRow(label, value = remove))
        }
        rows.retain(env.keys)
    }

    private fun transport(): String {
        if (type == REMOTE) return KiloBundle.message("settings.agentBehavior.mcp.edit.transport.remote")
        return KiloBundle.message("settings.agentBehavior.mcp.edit.transport.local")
    }

    private fun scroll(area: JBTextArea) = JBScrollPane(area).apply {
        viewportBorder = JBUI.Borders.empty(UiStyle.Gap.sm())
        preferredSize = JBDimension(0, UiStyle.Gap.pad() * 7)
    }

    private class ScrollContent : JPanel(BorderLayout()), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = UiStyle.Gap.pad()

        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int {
            if (orientation == SwingConstants.VERTICAL) return (visibleRect.height - UiStyle.Gap.pad()).coerceAtLeast(UiStyle.Gap.pad())
            return (visibleRect.width - UiStyle.Gap.pad()).coerceAtLeast(UiStyle.Gap.pad())
        }

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false
    }

    private companion object {
        const val LOCAL = "local"
        const val REMOTE = "remote"
        const val FIELD_COLUMNS = 36
        const val ENV_COLUMNS = 14
    }
}

private fun text(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
