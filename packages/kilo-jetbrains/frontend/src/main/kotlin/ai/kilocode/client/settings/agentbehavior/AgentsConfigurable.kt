package ai.kilocode.client.settings.agentbehavior

import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.BaseContentPanel
import ai.kilocode.client.settings.base.SettingsRow
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ConfigPatchDto
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JButton
import javax.swing.JComboBox

class AgentsConfigurable : AgentBehaviorConfigurableBase<AgentsSettingsUi>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.agents.displayName")
    override fun create(cs: CoroutineScope, dir: String) = AgentsSettingsUi(cs, dir)

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.agents" }
}

class AgentsSettingsUi(private val cs: CoroutineScope, private val dir: String) : BaseContentPanel(), AgentBehaviorPage {
    private var base = service<KiloAppService>().state.value.config?.defaultAgent
    private var draft = base
    private val rows = section(KiloBundle.message("settings.agentBehavior.agents.available"))

    init {
        next(UiStyle.Components.comingSoonButton(KiloBundle.message("settings.agentBehavior.browseMarketplace")))
        load()
    }

    private fun load() {
        cs.launch {
            val agents = service<KiloAgentBehaviorService>().agents(dir)
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                rows.removeAll()
                val names = listOf("") + agents.filter { it.mode != "subagent" && it.hidden != true }.map { it.name }
                rows.row(SettingsRow(KiloBundle.message("settings.agentBehavior.agents.default"), value = JComboBox(names.toTypedArray()).apply {
                    selectedItem = draft.orEmpty()
                    addActionListener { draft = (selectedItem as? String)?.takeIf { it.isNotBlank() } }
                }))
                if (agents.isEmpty()) rows.row(SettingsRow(KiloBundle.message("settings.agentBehavior.empty")))
                agents.forEach { item ->
                    val custom = item.native != true
                    val remove = if (custom) JButton(KiloBundle.message("settings.agentBehavior.remove")).apply {
                        addActionListener { cs.launch { service<KiloAgentBehaviorService>().removeAgent(dir, item.name); load() } }
                    } else null
                    val tags = listOfNotNull(item.mode, if (item.hidden == true) "hidden" else null, if (item.deprecated == true) "deprecated" else null)
                    rows.row(SettingsRow(item.displayName ?: item.name, listOfNotNull(item.description, tags.joinToString(", ").takeIf { it.isNotBlank() }).joinToString(" - "), remove))
                }
            }
        }
    }

    override fun modified(): Boolean = draft != base

    override fun applyDraft() {
        cs.launch {
            val state = service<KiloAppService>().updateConfig(ConfigPatchDto(values = mapOf("default_agent" to draft)))
            base = state?.config?.defaultAgent ?: draft
        }
    }

    override fun resetDraft() {
        draft = base
        load()
    }
}
