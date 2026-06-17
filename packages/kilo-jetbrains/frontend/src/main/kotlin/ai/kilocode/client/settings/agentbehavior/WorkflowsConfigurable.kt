package ai.kilocode.client.settings.agentbehavior

import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.BaseContentPanel
import ai.kilocode.client.settings.base.SettingsRow
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowsConfigurable : AgentBehaviorConfigurableBase<WorkflowsSettingsUi>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.workflows.displayName")
    override fun create(cs: CoroutineScope, dir: String) = WorkflowsSettingsUi(cs, dir)

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.workflows" }
}

class WorkflowsSettingsUi(cs: CoroutineScope, dir: String) : BaseContentPanel() {
    private val rows = section(KiloBundle.message("settings.agentBehavior.workflows.displayName"), KiloBundle.message("settings.agentBehavior.workflows.description"))

    init {
        cs.launch {
            val items = service<KiloAgentBehaviorService>().commands(dir)
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                rows.removeAll()
                if (items.isEmpty()) rows.row(SettingsRow(KiloBundle.message("settings.agentBehavior.empty")))
                items.forEach { item -> rows.row(SettingsRow("/${item.name}", item.description ?: item.template)) }
            }
        }
    }
}
