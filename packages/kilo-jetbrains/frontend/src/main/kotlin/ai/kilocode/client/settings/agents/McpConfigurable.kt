package ai.kilocode.client.settings.agents

import ai.kilocode.client.app.KiloAgentBehaviorService
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.SettingsBadge
import ai.kilocode.client.settings.base.SettingsListCell
import ai.kilocode.client.settings.base.SettingsListItem
import ai.kilocode.client.settings.base.SettingsListPanel
import ai.kilocode.client.settings.base.SettingsListSelection
import ai.kilocode.client.settings.base.SettingsMessageException
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.dto.ConfigPatchDto
import ai.kilocode.rpc.dto.McpConfigDto
import ai.kilocode.rpc.dto.McpStatusDto
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

class McpConfigurable : AgentBehaviorConfigurableBase<JComponent>() {
    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.agentBehavior.mcp.displayName")
    override fun create(cs: CoroutineScope, dir: String): JComponent = McpSettingsUi(cs, dir)
    override fun update(ui: JComponent, dir: String) {
        (ui as? McpSettingsUi)?.setDirectory(dir)
    }
    override fun scrollReadyShell() = false

    companion object { const val ID = "ai.kilocode.jetbrains.settings.agentBehavior.mcp" }
}

internal class McpSettingsUi(cs: CoroutineScope, dir: String) : SettingsListPanel(cs) {
    private var dir = dir

    init {
        start()
    }

    fun setDirectory(value: String) {
        if (value == dir) return
        dir = value
        reload()
    }

    override suspend fun fetch(): List<SettingsListItem> {
        val state = service<KiloAppService>().state.value
        val cfg = state.config?.mcp.orEmpty()
        val statuses = if (dir.isBlank()) {
            LOG.warn("mcp settings fetch skipped runtime status: missing project directory status=${state.status} config=${cfg.size}")
            emptyMap()
        } else {
            service<KiloAgentBehaviorService>().mcpStatus(dir).associateBy { it.name }
        }
        val names = (cfg.keys + statuses.keys).sorted()
        LOG.info("mcp settings fetch dir=$dir status=${state.status} config=${cfg.size} runtime=${statuses.size} total=${names.size}")
        if (names.isEmpty()) {
            LOG.warn("mcp settings fetch returned no servers dir=$dir status=${state.status} configLoaded=${state.config != null}")
        }
        return names.map { name -> item(name, cfg[name], statuses[name]) }
    }

    override fun onCell(key: String, cellId: String) {
        when (cellId) {
            CONNECT_CELL -> mutate(key) { service<KiloAgentBehaviorService>().mcpConnect(dir, key) }
            DISCONNECT_CELL -> mutate(key) { service<KiloAgentBehaviorService>().mcpDisconnect(dir, key) }
            AUTH_CELL -> mutate(key) { service<KiloAgentBehaviorService>().mcpAuthenticate(dir, key) }
            REMOVE_CELL -> mutateAndReload(selectionIndex()) {
                service<KiloAppService>().updateConfig(ConfigPatchDto(mcp = mapOf(key to null)))
                    ?: throw SettingsMessageException(KiloBundle.message("settings.agentBehavior.save.failed"))
                true
            }
        }
    }

    override fun searchPlaceholder() = KiloBundle.message("settings.agentBehavior.mcp.search")

    private fun item(name: String, cfg: McpConfigDto?, status: McpStatusDto?) = object : SettingsListItem {
        override val key = name
        override val title = name
        override val description = description(cfg, status)
        override val badges = badges(cfg, status)
        override val cells = cells(cfg, status)
    }

    private fun description(cfg: McpConfigDto?, status: McpStatusDto?): String? {
        val parts = listOfNotNull(
            cfg?.url?.takeIf { it.isNotBlank() },
            cfg?.command?.takeIf { it.isNotEmpty() }?.joinToString(" "),
            status?.error?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" - ").takeIf { it.isNotBlank() }
    }

    private fun badges(cfg: McpConfigDto?, status: McpStatusDto?): List<SettingsBadge> = listOfNotNull(
        SettingsBadge(statusLabel(status), statusStyle(status)).takeIf { status != null },
        SettingsBadge(cfg?.type ?: KiloBundle.message("settings.agentBehavior.mcp.configured")).takeIf { cfg != null },
    )

    private fun cells(cfg: McpConfigDto?, status: McpStatusDto?): List<SettingsListCell> = listOfNotNull(
        SettingsListCell(AUTH_CELL, KiloBundle.message("settings.agentBehavior.mcp.signIn")).takeIf {
            status?.status == NEEDS_AUTH
        },
        SettingsListCell(
            if (status?.status == CONNECTED) DISCONNECT_CELL else CONNECT_CELL,
            if (status?.status == CONNECTED) KiloBundle.message("settings.agentBehavior.mcp.disconnect")
            else KiloBundle.message("settings.agentBehavior.mcp.connect"),
        ),
        SettingsListCell(REMOVE_CELL, KiloBundle.message("settings.agentBehavior.remove")).takeIf { cfg != null },
    )

    private fun mutate(name: String, block: suspend () -> Boolean) {
        mutateAndReload(SettingsListSelection.Key(name)) {
            if (!block()) throw SettingsMessageException(KiloBundle.message("settings.agentBehavior.mcp.action.failed"))
            true
        }
    }

    private companion object {
        const val CONNECTED = "connected"
        const val FAILED = "failed"
        const val NEEDS_AUTH = "needs_auth"
        const val NEEDS_REGISTRATION = "needs_client_registration"
        const val DISABLED = "disabled"
        const val CONNECT_CELL = "connect"
        const val DISCONNECT_CELL = "disconnect"
        const val AUTH_CELL = "auth"
        const val REMOVE_CELL = "remove"
        val LOG = KiloLog.create(McpSettingsUi::class.java)

        fun statusLabel(status: McpStatusDto?): String {
            val value = status?.status ?: return ""
            return when (value) {
                CONNECTED -> KiloBundle.message("settings.agentBehavior.mcp.status.connected")
                FAILED -> KiloBundle.message("settings.agentBehavior.mcp.status.failed")
                NEEDS_AUTH -> KiloBundle.message("settings.agentBehavior.mcp.status.needsAuth")
                NEEDS_REGISTRATION -> KiloBundle.message("settings.agentBehavior.mcp.status.needsRegistration")
                DISABLED -> KiloBundle.message("settings.agentBehavior.mcp.status.disabled")
                else -> value
            }
        }

        fun statusStyle(status: McpStatusDto?): UiStyle.Badge.Style {
            return when (status?.status) {
                CONNECTED -> UiStyle.Badge.Highlight
                FAILED,
                NEEDS_AUTH,
                NEEDS_REGISTRATION,
                -> UiStyle.Badge.Alert
                else -> UiStyle.Badge.Secondary
            }
        }
    }
}
