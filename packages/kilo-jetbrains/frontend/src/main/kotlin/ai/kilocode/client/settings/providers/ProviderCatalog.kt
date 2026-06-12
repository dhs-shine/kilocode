package ai.kilocode.client.settings.providers

import ai.kilocode.rpc.dto.ProviderAuthMethodDto
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto
import ai.kilocode.client.plugin.KiloBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

internal const val KILO_PROVIDER_ID = "kilo"
internal const val CUSTOM_PROVIDER_PACKAGE = "@ai-sdk/openai-compatible"

internal val POPULAR_PROVIDER_IDS = listOf(KILO_PROVIDER_ID, "anthropic", "deepseek", "openai", "google", "openrouter", "vercel")

internal fun isPopularProvider(id: String) = id in POPULAR_PROVIDER_IDS

internal fun popularProviderIndex(id: String): Int {
    val index = POPULAR_PROVIDER_IDS.indexOf(id)
    return if (index >= 0) index else Int.MAX_VALUE
}

internal fun providerDescription(provider: ProviderSettingsProviderDto): String {
    providerNoteKey(provider.id)?.let { return KiloBundle.message(it) }
    val source = provider.source ?: "catalog"
    val models = provider.models.size
    return "$source · $models models"
}

internal fun providerIcon(provider: ProviderSettingsProviderDto): Icon {
    if (provider.id == KILO_PROVIDER_ID) return IconLoader.getIcon("/icons/kilo.svg", ProviderIcons::class.java)
    return AllIcons.Nodes.Plugin
}

private object ProviderIcons

private fun providerNoteKey(id: String): String? {
    if (id == KILO_PROVIDER_ID) return "settings.providers.note.kilo"
    if (id == "opencode") return "settings.providers.note.opencode"
    if (id == "anthropic") return "settings.providers.note.anthropic"
    if (id == "deepseek") return "settings.providers.note.deepseek"
    if (id.startsWith("github-copilot")) return "settings.providers.note.copilot"
    if (id == "openai") return "settings.providers.note.openai"
    if (id == "google") return "settings.providers.note.google"
    if (id == "openrouter") return "settings.providers.note.openrouter"
    if (id == "vercel") return "settings.providers.note.vercel"
    return null
}

internal fun providerMethods(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto): List<ProviderAuthMethodDto> {
    val methods = state.auth[provider.id]
    if (!methods.isNullOrEmpty()) return methods
    return listOf(ProviderAuthMethodDto("api", "API key"))
}

internal fun hiddenProvider(provider: ProviderSettingsProviderDto) = provider.id == "openai-compatible"

internal fun configured(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto, ids: Set<String>) =
    provider.id in ids || provider.key != null || provider.source == "config" || provider.id in state.config
