package ai.kilocode.client.settings

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Parent settings entry under Settings -> Tools -> Kilo.
 *
 * Acts as a group node; actual functionality lives in child configurables
 * (e.g. [UserProfileConfigurable]).
 */
class KiloSettingsConfigurable : Configurable {

    override fun getDisplayName(): String = KiloBundle.message("settings.kilo.displayName")

    override fun createComponent(): JComponent =
        JLabel(KiloBundle.message("settings.kilo.description"))

    override fun isModified(): Boolean = false

    override fun apply() = Unit
}
