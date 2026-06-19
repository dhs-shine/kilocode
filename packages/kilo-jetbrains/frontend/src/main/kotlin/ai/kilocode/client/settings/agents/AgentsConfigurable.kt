package ai.kilocode.client.settings.agents

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
import ai.kilocode.rpc.dto.AgentDetailDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import javax.swing.JComboBox

private val edt = Dispatchers.EDT + ModalityState.any().asContextElement()

class AgentsConfigurable : AgentBehaviorConfigurableBase<JComponent>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.agents.displayName")
    override fun create(cs: CoroutineScope, dir: String): JComponent = AgentsSettingsUi(cs, dir)
    override fun scrollReadyShell() = false

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.agents" }
}

internal class AgentsSettingsUi(private val cs: CoroutineScope, private val dir: String) : SettingsListPanel(cs), AgentBehaviorPage {
    private var base = agentsDraft(service<KiloAppService>().state.value.config, emptyList())
    private var draft = base
    private var details = emptyList<AgentDetailDto>()
    private var names = emptyList<String>()
    private lateinit var picker: JComboBox<String>
    private var syncing = false

    init {
        start()
    }

    override suspend fun fetch(): List<SettingsListItem> {
        val agents = service<KiloAgentBehaviorService>().agents(dir)
        val next = agentsDraft(service<KiloAppService>().state.value.config, agents)
        if (draft.agents.isEmpty() || !modified()) {
            base = next
            draft = next
        } else {
            base = next
            draft = draft.copy(agents = next.agents + draft.agents)
        }
        details = agents
        syncNames()
        return rows()
    }

    override fun extraActions(): List<AnAction> = listOf(addAction())

    override fun toolbarRight(): JComponent = Stack.horizontal(UiStyle.Gap.sm())
        .next(JBLabel(KiloBundle.message("settings.agentBehavior.agents.default")))
        .next(makePicker())

    override fun afterApply() {
        syncPicker()
    }

    private fun makePicker(): JComboBox<String> {
        if (::picker.isInitialized) return picker
        picker = JComboBox(names.toTypedArray()).apply {
            selectedItem = draft.defaultAgent.orEmpty()
            addActionListener {
                if (syncing) return@addActionListener
                draft = draft.copy(defaultAgent = (selectedItem as? String)?.takeIf { it.isNotBlank() })
            }
        }
        return picker
    }

    override fun onCell(key: String, cellId: String) {
        if (cellId != EDIT_CELL) return
        val agent = draft.agents[key] ?: return
        val dialog = AgentEditDialog(agent)
        if (!dialog.showAndGet()) return
        draft = updateAgent(draft, dialog.result())
        syncNames()
        syncPicker()
        view.update(rows())
    }

    override fun searchPlaceholder() = KiloBundle.message("settings.agentBehavior.agents.search")

    override fun modified(): Boolean = !savedMatches(base, draft)

    override fun applyDraft() {
        val change = patch(base, draft) ?: return
        val applied = draft
        cs.launch {
            val state = service<KiloAppService>().updateConfig(change)
            val next = state?.config?.let { agentsDraft(it, details) } ?: applied
            withContext(edt) {
                base = next
                if (savedMatches(applied, draft)) draft = next
                syncNames()
                syncPicker()
                view.update(rows())
            }
        }
    }

    override fun resetDraft() {
        draft = base
        syncNames()
        syncPicker()
        view.update(rows())
    }

    private fun rows(): List<SettingsListItem> = draft.agents.values.map { item ->
        val custom = !item.native
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
                SettingsBadge(KiloBundle.message("settings.agentBehavior.badge.hidden")).takeIf { item.hidden },
                SettingsBadge(KiloBundle.message("settings.agentBehavior.badge.disabled")).takeIf { item.disable },
                SettingsBadge(
                    KiloBundle.message("settings.agentBehavior.badge.deprecated"),
                    UiStyle.Badge.Alert,
                ).takeIf { item.deprecated },
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

    private fun syncNames() {
        names = listOf("") + draft.agents.values
            .filter { KiloCliParser.defaultAgentCandidate(it.mode, it.hidden) && !it.disable }
            .map { it.name }
    }

    private fun syncPicker() {
        if (!::picker.isInitialized) return
        syncing = true
        try {
            picker.removeAllItems()
            names.forEach { picker.addItem(it) }
            picker.selectedItem = draft.defaultAgent.orEmpty()
        } finally {
            syncing = false
        }
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
