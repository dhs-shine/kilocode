package ai.kilocode.client.settings.providers

import ai.kilocode.rpc.dto.ProviderAuthMethodDto
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto

internal val POPULAR_PROVIDER_IDS = listOf("kilo", "anthropic", "deepseek", "openai", "google", "openrouter", "vercel")

internal fun isPopularProvider(id: String) = id in POPULAR_PROVIDER_IDS

internal fun popularProviderIndex(id: String): Int {
    val index = POPULAR_PROVIDER_IDS.indexOf(id)
    return if (index >= 0) index else Int.MAX_VALUE
}

internal fun providerDescription(provider: ProviderSettingsProviderDto): String {
    val source = provider.source ?: "catalog"
    val models = provider.models.size
    return "$source · $models models"
}

internal fun providerMethods(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto): List<ProviderAuthMethodDto> {
    val methods = state.auth[provider.id]
    if (!methods.isNullOrEmpty()) return methods
    return listOf(ProviderAuthMethodDto("api", "API key"))
}

internal fun configured(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto, ids: Set<String>) =
    provider.id in ids || provider.key != null || provider.source == "config" || provider.id in state.config
