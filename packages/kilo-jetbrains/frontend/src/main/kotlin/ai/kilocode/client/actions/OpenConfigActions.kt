package ai.kilocode.client.actions

import ai.kilocode.client.KiloNotifications
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.SessionManager
import ai.kilocode.client.telemetry.Telemetry
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

abstract class ConfigAction(
    private val label: String,
    text: String,
    description: String,
) : AnAction(text, description, null), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    protected fun text(path: String?) = KiloBundle.message(label, path ?: "...")

    protected fun failed() {
        KiloNotifications.error(KiloBundle.message("action.Kilo.OpenConfig.failed"))
    }
}

class OpenLocalConfigAction : ConfigAction(
    label = "action.Kilo.OpenLocalConfig.text",
    text = KiloBundle.message("action.Kilo.OpenLocalConfig.text", "..."),
    description = KiloBundle.message("action.Kilo.OpenLocalConfig.description"),
) {
    override fun update(e: AnActionEvent) {
        val dir = directory(e)
        e.presentation.isEnabled = dir != null
        e.presentation.text = text(dir?.let { service<KiloWorkspaceService>().localConfigPath(it) })
    }

    override fun actionPerformed(e: AnActionEvent) {
        val dir = directory(e) ?: return
        Telemetry.send("Config Opened", mapOf("surface" to "tool_window", "scope" to "local"))
        service<KiloWorkspaceService>().openLocalConfig(dir) { ok ->
            if (!ok) failed()
        }
    }

    private fun directory(e: AnActionEvent): String? {
        return e.getData(SessionManager.WORKSPACE_KEY)?.directory ?: e.project?.basePath
    }
}

class OpenGlobalConfigAction : ConfigAction(
    label = "action.Kilo.OpenGlobalConfig.text",
    text = KiloBundle.message("action.Kilo.OpenGlobalConfig.text", "..."),
    description = KiloBundle.message("action.Kilo.OpenGlobalConfig.description"),
) {
    override fun update(e: AnActionEvent) {
        e.presentation.text = text(service<KiloWorkspaceService>().globalConfigPath())
    }

    override fun actionPerformed(e: AnActionEvent) {
        Telemetry.send("Config Opened", mapOf("surface" to "tool_window", "scope" to "global"))
        service<KiloWorkspaceService>().openGlobalConfig { ok ->
            if (!ok) failed()
        }
    }
}
