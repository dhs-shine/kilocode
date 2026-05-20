package ai.kilocode.client.settings

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.profile.UserProfileConfigurable
import com.intellij.ide.DataManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Root settings entry under Settings -> Tools -> Kilo Code.
 *
 * Displays a brief description and links to each child settings page.
 * Acts as a [SearchableConfigurable.Parent] so the node is selectable and
 * shows its own index content while also hosting child configurables.
 */
class KiloSettingsConfigurable : SearchableConfigurable.Parent {

    private val kids: Array<Configurable> = arrayOf(UserProfileConfigurable())

    override fun getId(): String = ID

    override fun getDisplayName(): String = KiloBundle.message("settings.kilo.displayName")

    override fun hasOwnContent(): Boolean = true

    override fun getConfigurables(): Array<Configurable> = kids

    override fun createComponent(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8, 0, 0, 0)

        val desc = JBLabel(KiloBundle.message("settings.kilo.description"))
        desc.border = JBUI.Borders.emptyBottom(12)
        panel.add(desc)

        for (child in kids) {
            val link = ActionLink(child.displayName) { e ->
                val src = e.source as? JComponent ?: return@ActionLink
                val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(src)) ?: return@ActionLink
                open(settings, child)
            }
            link.border = JBUI.Borders.emptyBottom(4)
            panel.add(link)
        }

        return panel
    }

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    internal fun open(settings: Settings, cfg: Configurable) {
        val id = (cfg as? SearchableConfigurable)?.id ?: cfg.javaClass.name
        settings.select(settings.find(id))
    }

    companion object {
        const val ID = "ai.kilocode.jetbrains.settings"
    }
}
