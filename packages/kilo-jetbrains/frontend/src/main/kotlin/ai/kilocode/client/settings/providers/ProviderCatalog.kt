package ai.kilocode.client.settings.providers

import ai.kilocode.rpc.dto.ProviderAuthMethodDto
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto
import ai.kilocode.client.plugin.KiloBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlin.math.min
import kotlin.math.roundToInt

internal const val KILO_PROVIDER_ID = "kilo"
internal const val CUSTOM_PROVIDER_PACKAGE = "@ai-sdk/openai-compatible"

internal val POPULAR_PROVIDER_IDS = listOf(KILO_PROVIDER_ID, "anthropic", "deepseek", "openai", "google", "openrouter", "vercel")

internal fun isPopularProvider(id: String) = id in POPULAR_PROVIDER_IDS

internal fun popularProviderIndex(id: String): Int {
    val index = POPULAR_PROVIDER_IDS.indexOf(id)
    return if (index >= 0) index else Int.MAX_VALUE
}

internal fun providerDescription(provider: ProviderSettingsProviderDto): String {
    provider.description?.takeIf { it.isNotBlank() }?.let { return it }
    provider.metadata?.noteKey?.let { key -> KiloBundle.optional(key)?.let { return it } }
    provider.metadata?.note?.let { return it }
    return ""
}

internal fun providerIcon(provider: ProviderSettingsProviderDto): Icon {
    val id = provider.metadata?.icon ?: provider.id
    return ProviderIcons.icon(id)
}

private object ProviderIcons {
    private val cache = ConcurrentHashMap<String, Icon>()

    fun icon(id: String): Icon = cache.computeIfAbsent(id) { key ->
        val icon = IconLoader.findIcon("/icons/providers/$key.svg", ProviderIcons::class.java)
        FixedProviderIcon(icon ?: IconLoader.findIcon("/icons/providers/synthetic.svg", ProviderIcons::class.java) ?: AllIcons.Nodes.Plugin)
    }
}

private class FixedProviderIcon(private val icon: Icon) : Icon {
    override fun getIconWidth() = JBUI.scale(20)

    override fun getIconHeight() = JBUI.scale(20)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val width = icon.iconWidth.coerceAtLeast(1)
        val height = icon.iconHeight.coerceAtLeast(1)
        val size = min(iconWidth.toDouble() / width, iconHeight.toDouble() / height)
        val w = (width * size).roundToInt().coerceAtLeast(1)
        val h = (height * size).roundToInt().coerceAtLeast(1)
        val copy = g.create() as Graphics2D
        try {
            copy.translate(x + (iconWidth - w) / 2, y + (iconHeight - h) / 2)
            copy.scale(size, size)
            icon.paintIcon(c, copy, 0, 0)
        } finally {
            copy.dispose()
        }
    }
}

internal fun providerMethods(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto): List<ProviderAuthMethodDto> {
    val methods = state.auth[provider.id]
    if (!methods.isNullOrEmpty()) return methods
    return listOf(ProviderAuthMethodDto("api", "API key"))
}

internal fun hiddenProvider(provider: ProviderSettingsProviderDto) = provider.id == "openai-compatible"

internal fun configured(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto, ids: Set<String>) =
    provider.id in ids || provider.key != null || provider.source == "config" || provider.id in state.config
