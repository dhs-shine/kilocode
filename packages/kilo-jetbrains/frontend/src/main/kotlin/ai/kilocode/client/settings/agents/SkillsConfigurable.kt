package ai.kilocode.client.settings.agents

import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.SettingsBadge
import ai.kilocode.client.settings.base.SettingsListCell
import ai.kilocode.client.settings.base.SettingsListItem
import ai.kilocode.client.settings.base.SettingsListPanel
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ConfigPatchDto
import ai.kilocode.rpc.dto.SkillsPatchDto
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.JComponent

class SkillsConfigurable : AgentBehaviorConfigurableBase<JComponent>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.skills.displayName")
    override fun create(cs: CoroutineScope, dir: String): JComponent = SkillsSettingsUi(cs, dir)
    override fun scrollReadyShell() = false

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.skills" }
}

internal class SkillsSettingsUi(private val cs: CoroutineScope, private val dir: String) : SettingsListPanel(cs), AgentBehaviorPage {
    private var basePaths = service<KiloAppService>().state.value.config?.skills?.paths.orEmpty()
    private var baseUrls = service<KiloAppService>().state.value.config?.skills?.urls.orEmpty()
    private var paths = basePaths
    private var urls = baseUrls

    init {
        start()
    }

    override suspend fun fetch(): List<SettingsListItem> {
        val config = service<KiloAppService>().state.value.config?.skills
        basePaths = config?.paths.orEmpty()
        baseUrls = config?.urls.orEmpty()
        if (paths == basePaths) paths = basePaths
        if (urls == baseUrls) urls = baseUrls
        val discovered = service<KiloAgentBehaviorService>().skills(dir)
        return localRows() + discovered.map { skill ->
            val builtin = skill.location == "builtin"
            object : SettingsListItem {
                override val key = "skill:${skill.location}"
                override val title = skill.name
                override val description = skill.description ?: skill.location
                override val section = KiloBundle.message("settings.agentBehavior.skills.discovered")
                override val badges = listOf(
                    if (builtin) SettingsBadge(KiloBundle.message("settings.agentBehavior.badge.builtin"))
                    else SettingsBadge(KiloBundle.message("settings.agentBehavior.badge.custom"), UiStyle.Badge.Primary),
                )
                override val cells = if (builtin) emptyList() else listOf(removeCell())
            }
        }
    }

    private fun localRows(): List<SettingsListItem> {
        val pathRows = paths.mapIndexed { index, value -> local("path:$index", value, KiloBundle.message("settings.agentBehavior.skills.paths")) }
        val urlRows = urls.mapIndexed { index, value -> local("url:$index", value, KiloBundle.message("settings.agentBehavior.skills.urls")) }
        return pathRows + urlRows
    }

    private fun local(key: String, value: String, section: String) = object : SettingsListItem {
        override val key = key
        override val title = value
        override val section = section
        override val cells = listOf(removeCell())
    }

    override fun onCell(key: String, cellId: String) {
        if (cellId != REMOVE_CELL) return
        when {
            key.startsWith("path:") -> {
                val index = key.removePrefix("path:").toIntOrNull() ?: return
                paths = paths.filterIndexed { idx, _ -> idx != index }
                reload()
            }
            key.startsWith("url:") -> {
                val index = key.removePrefix("url:").toIntOrNull() ?: return
                urls = urls.filterIndexed { idx, _ -> idx != index }
                reload()
            }
            key.startsWith("skill:") -> {
                val location = key.removePrefix("skill:")
                cs.launch {
                    service<KiloAgentBehaviorService>().removeSkill(dir, location)
                    reload()
                }
            }
        }
    }

    override fun searchPlaceholder() = KiloBundle.message("settings.agentBehavior.skills.search")

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
        reload()
    }

    private fun removeCell() = SettingsListCell(REMOVE_CELL, KiloBundle.message("settings.agentBehavior.remove"))

    private companion object { const val REMOVE_CELL = "remove" }
}
