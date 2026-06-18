package ai.kilocode.client.settings.agentbehavior

import ai.kilocode.cli.KiloCliParser
import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.SettingsBadge
import ai.kilocode.client.settings.base.SettingsListCell
import ai.kilocode.client.settings.base.SettingsListItem
import ai.kilocode.client.settings.base.SettingsListPanel
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.rpc.dto.ConfigPatchDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.JComponent
import javax.swing.JComboBox

class AgentsConfigurable : AgentBehaviorConfigurableBase<JComponent>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.agents.displayName")
    override fun create(cs: CoroutineScope, dir: String): JComponent = AgentsSettingsUi(cs, dir)
    override fun scrollReadyShell() = false

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.agents" }
}

internal class AgentsSettingsUi(private val cs: CoroutineScope, private val dir: String) : SettingsListPanel(cs), AgentBehaviorPage {
    private var base = service<KiloAppService>().state.value.config?.defaultAgent
    private var draft = base
    private var names = emptyList<String>()
    private lateinit var picker: JComboBox<String>

    init {
        start()
    }

    override suspend fun fetch(): List<SettingsListItem> {
        val agents = service<KiloAgentBehaviorService>().agents(dir)
        names = listOf("") + agents.filter { KiloCliParser.defaultAgentCandidate(it.mode, it.hidden) }.map { it.name }
        return agents.map { item ->
            val custom = item.native != true
            object : SettingsListItem {
                override val key = item.name
                override val title = item.displayName ?: item.name
                override val description = item.description
                override val section = KiloBundle.message("settings.agentBehavior.agents.available")
                override val badges = listOfNotNull(
                    SettingsBadge(
                        KiloBundle.message("settings.agentBehavior.badge.subagent"),
                        UiStyle.Badge.Primary,
                    ).takeIf { KiloCliParser.isSubagent(item.mode) },
                    SettingsBadge(
                        KiloBundle.message("settings.agentBehavior.badge.custom"),
                        UiStyle.Badge.Primary,
                    ).takeIf { custom },
                    SettingsBadge(KiloBundle.message("settings.agentBehavior.badge.hidden")).takeIf { item.hidden == true },
                    SettingsBadge(
                        KiloBundle.message("settings.agentBehavior.badge.deprecated"),
                        UiStyle.Badge.Alert,
                    ).takeIf { item.deprecated == true },
                )
                override val cells = listOfNotNull(
                    SettingsListCell(EDIT_CELL, KiloBundle.message("settings.agentBehavior.edit")),
                    SettingsListCell(
                        DELETE_CELL,
                        KiloBundle.message("settings.agentBehavior.delete"),
                        icon = AllIcons.Actions.GC,
                        iconOnly = true,
                    ).takeIf { custom },
                )
            }
        }
    }

    override fun extraActions(): List<AnAction> = listOf(addAction())

    override fun toolbarRight(): JComponent = Stack.horizontal(UiStyle.Gap.sm())
        .next(JBLabel(KiloBundle.message("settings.agentBehavior.agents.default")))
        .next(makePicker())

    override fun afterApply() {
        if (!::picker.isInitialized) return
        val selected = draft.orEmpty()
        picker.removeAllItems()
        names.forEach { picker.addItem(it) }
        picker.selectedItem = selected
    }

    private fun makePicker(): JComboBox<String> {
        if (::picker.isInitialized) return picker
        picker = JComboBox(names.toTypedArray()).apply {
            selectedItem = draft.orEmpty()
            addActionListener { draft = (selectedItem as? String)?.takeIf { it.isNotBlank() } }
        }
        return picker
    }

    override fun onCell(key: String, cellId: String) = Unit

    override fun searchPlaceholder() = KiloBundle.message("settings.agentBehavior.agents.search")

    override fun modified(): Boolean = draft != base

    override fun applyDraft() {
        cs.launch {
            val state = service<KiloAppService>().updateConfig(ConfigPatchDto(values = mapOf(KiloCliParser.CONFIG_DEFAULT_AGENT to draft)))
            base = state?.config?.defaultAgent ?: draft
        }
    }

    override fun resetDraft() {
        draft = base
        if (::picker.isInitialized) picker.selectedItem = draft.orEmpty()
    }

    private fun addAction(): DefaultActionGroup = DefaultActionGroup(
        KiloBundle.message("settings.agentBehavior.agents.add"),
        true,
    ).apply {
        templatePresentation.icon = AllIcons.General.Add
        add(PlaceholderAction(KiloBundle.message("settings.agentBehavior.agents.create")))
        add(PlaceholderAction(KiloBundle.message("settings.agentBehavior.agents.import")))
    }

    private companion object {
        const val EDIT_CELL = "edit"
        const val DELETE_CELL = "delete"
    }
}

private class PlaceholderAction(text: String) : DumbAwareAction(text) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) = Unit
}
