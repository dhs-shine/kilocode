package ai.kilocode.client.settings.providers

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.annotations.RequiresEdt
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

    @RequiresEdt
    override fun createComponent(): JComponent {
        checkEdt()
        val cs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = cs
        val dir = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault }?.basePath.orEmpty()
        val panel = ProvidersSettingsUi(cs, dir)
        ui = panel
        return panel
    }

    override fun isModified(): Boolean = false
    override fun apply() = Unit
    @RequiresEdt
    override fun reset() {
        checkEdt()
        ui?.reload()
    }

    override fun disposeUIResources() {
        val panel = ui
        val cs = scope
        ui = null
        scope = null
        cs?.cancel()
        val app = ApplicationManager.getApplication()
        if (panel != null && app.isDispatchThread) {
            panel.dispose()
            return
        }
        if (panel != null) {
            app.invokeLater({
                panel.dispose()
            }, ModalityState.any())
            return
        }
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) { "Provider configurable UI must run on EDT" }
    }

    companion object {
        const val ID = "ai.kilocode.jetbrains.settings.providers"
    }
}
