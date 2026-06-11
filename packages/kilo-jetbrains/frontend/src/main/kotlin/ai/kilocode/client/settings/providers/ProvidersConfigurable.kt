package ai.kilocode.client.settings.providers

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.swing.JComponent

class ProvidersConfigurable : SearchableConfigurable, Configurable.NoScroll {
    private var ui: ProvidersSettingsUi? = null
    private var scope: CoroutineScope? = null

    override fun getId(): String = ID
    override fun getDisplayName(): String = KiloBundle.message("settings.providers.displayName")

    override fun createComponent(): JComponent {
        val cs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = cs
        val dir = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault }?.basePath.orEmpty()
        val panel = ProvidersSettingsUi(cs, dir)
        ui = panel
        return panel
    }

    override fun isModified(): Boolean = false
    override fun apply() = Unit
    override fun reset() = ui?.reload() ?: Unit

    override fun disposeUIResources() {
        val panel = ui
        val cs = scope
        ui = null
        scope = null
        val app = ApplicationManager.getApplication()
        if (panel != null && app.isDispatchThread) {
            panel.dispose()
            cs?.cancel()
            return
        }
        if (panel != null) {
            app.invokeLater({
                panel.dispose()
                cs?.cancel()
            }, ModalityState.any())
            return
        }
        cs?.cancel()
    }

    companion object {
        const val ID = "ai.kilocode.jetbrains.settings.providers"
    }
}
