package ai.kilocode

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId

object KiloPlugin {
    const val ID = "ai.kilocode.jetbrains"

    val id: PluginId = PluginId.getId(ID)

    fun descriptor(): PluginDescriptor? {
        val loader = KiloPlugin::class.java.classLoader as? PluginAwareClassLoader ?: return null
        val descriptor = loader.pluginDescriptor
        if (descriptor.pluginId != id) return null
        return descriptor
    }

    fun version() = descriptor()?.version

    fun isRc() = version()?.contains("-rc.", ignoreCase = true) == true
}
