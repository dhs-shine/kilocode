package ai.kilocode.client.settings.agentbehavior

import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.BaseContentPanel
import ai.kilocode.client.settings.base.SettingsListEditor
import ai.kilocode.client.settings.base.SettingsRow
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ConfigPatchDto
import ai.kilocode.rpc.dto.SkillsPatchDto
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JButton

class SkillsConfigurable : AgentBehaviorConfigurableBase<SkillsSettingsUi>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.skills.displayName")
    override fun create(cs: CoroutineScope, dir: String) = SkillsSettingsUi(cs, dir)

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.skills" }
}

class SkillsSettingsUi(private val cs: CoroutineScope, private val dir: String) : BaseContentPanel(), AgentBehaviorPage {
    private var basePaths = service<KiloAppService>().state.value.config?.skills?.paths.orEmpty()
    private var baseUrls = service<KiloAppService>().state.value.config?.skills?.urls.orEmpty()
    private var paths = basePaths
    private var urls = baseUrls
    private val pathEditor = SettingsListEditor(paths) { paths = it }
    private val urlEditor = SettingsListEditor(urls) { urls = it }
    private val discovered = section(KiloBundle.message("settings.agentBehavior.skills.discovered"))

    init {
        next(UiStyle.Components.comingSoonButton(KiloBundle.message("settings.agentBehavior.browseMarketplace")))
        section(KiloBundle.message("settings.agentBehavior.skills.paths")).row(SettingsRow(KiloBundle.message("settings.agentBehavior.skills.paths"), value = pathEditor))
        section(KiloBundle.message("settings.agentBehavior.skills.urls")).row(SettingsRow(KiloBundle.message("settings.agentBehavior.skills.urls"), value = urlEditor))
        load()
    }

    private fun load() {
        cs.launch {
            val items = service<KiloAgentBehaviorService>().skills(dir)
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                discovered.removeAll()
                if (items.isEmpty()) discovered.row(SettingsRow(KiloBundle.message("settings.agentBehavior.empty")))
                items.forEach { skill ->
                    val remove = JButton(KiloBundle.message("settings.agentBehavior.remove")).apply {
                        border = JBUI.Borders.empty()
                        addActionListener { cs.launch { service<KiloAgentBehaviorService>().removeSkill(dir, skill.location); load() } }
                    }
                    discovered.row(SettingsRow(skill.name, skill.description ?: skill.location, remove))
                }
            }
        }
    }

    override fun modified(): Boolean = paths != basePaths || urls != baseUrls

    override fun applyDraft() {
        cs.launch {
            val state = service<KiloAppService>().updateConfig(ConfigPatchDto(skills = SkillsPatchDto(paths = paths, urls = urls)))
            basePaths = state?.config?.skills?.paths ?: paths
            baseUrls = state?.config?.skills?.urls ?: urls
        }
    }

    override fun resetDraft() {
        paths = basePaths
        urls = baseUrls
        pathEditor.update(paths)
        urlEditor.update(urls)
    }
}
