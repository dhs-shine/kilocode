package ai.kilocode.client.settings.agents

import ai.kilocode.client.settings.base.KiloReadyConfigurable
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

abstract class AgentBehaviorConfigurableBase<T : JComponent> : KiloReadyConfigurable() {
    private var panel: T? = null

    override fun createReadyComponent(cs: CoroutineScope): JComponent {
        val dir = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault }?.basePath.orEmpty()
        val ui = create(cs, dir)
        panel = ui
        return ui
    }

    override fun isModifiedReady(): Boolean = (panel as? AgentBehaviorPage)?.modified() == true

    override fun applyReady() {
        (panel as? AgentBehaviorPage)?.applyDraft()
    }

    override fun resetReady() {
        (panel as? AgentBehaviorPage)?.resetDraft()
    }

    override fun disposeReadyComponent(component: JComponent) {
        panel = null
    }

    protected abstract fun create(cs: CoroutineScope, dir: String): T
}

interface AgentBehaviorPage {
    fun modified(): Boolean = false
    fun applyDraft() = Unit
    fun resetDraft() = Unit
}
