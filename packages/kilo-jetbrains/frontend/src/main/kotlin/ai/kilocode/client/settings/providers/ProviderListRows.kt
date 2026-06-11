package ai.kilocode.client.settings.providers

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelSearch
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto

internal enum class ProviderListAction {
    CONNECT,
    OAUTH,
    DISCONNECT,
    ENABLE,
}

internal data class ProviderListRow(
    val provider: ProviderSettingsProviderDto,
    val section: String,
    val actions: List<ProviderListAction>,
) {
    val key: String get() = provider.id

    fun enabled(action: ProviderListAction) = action != ProviderListAction.DISCONNECT || provider.source != "env"
}

internal fun providerListRows(state: ProviderSettingsDto, query: String): List<ProviderListRow> {
    val q = query.trim()
    val ids = state.connected.toSet()
    val disabled = state.disabled.toSet()
    val filtered = state.providers.filter { ModelSearch.matches(q, it.name) }
    val popular = filtered
        .filter { it.id != "kilo" }
        .filter { it.id !in disabled }
        .filter { !configured(it, state, ids) }
        .filter { isPopularProvider(it.id) }
        .sortedWith(compareBy<ProviderSettingsProviderDto> { popularProviderIndex(it.id) }.thenBy { it.name.lowercase() }.thenBy { it.id })
    val popularIds = popular.mapTo(mutableSetOf()) { it.id }
    val all = filtered
        .filter { it.id !in popularIds }
        .sortedWith(compareBy<ProviderSettingsProviderDto> { it.name.lowercase() }.thenBy { it.id })
    val rows = mutableListOf<ProviderListRow>()
    rows += popular.map { ProviderListRow(it, KiloBundle.message("settings.providers.popular"), providerActions(it, state, disabled)) }
    rows += all.map { ProviderListRow(it, KiloBundle.message("settings.providers.all"), providerActions(it, state, disabled)) }
    return rows
}

internal fun providerListIndex(rows: List<ProviderListRow>, key: String?): Int {
    if (key == null) return if (rows.isEmpty()) -1 else 0
    return rows.indexOfFirst { it.key == key }
}

internal fun providerListIndex(rows: List<ProviderListRow>, index: Int): Int {
    if (rows.isEmpty()) return -1
    return index.coerceIn(0, rows.lastIndex)
}

internal fun providerListSectionTitle(rows: List<ProviderListRow>, index: Int): String? {
    val row = rows.getOrNull(index) ?: return null
    val prev = rows.getOrNull(index - 1)
    return if (prev?.section != row.section) row.section else null
}

internal fun providerActions(
    provider: ProviderSettingsProviderDto,
    state: ProviderSettingsDto,
    disabled: Set<String> = state.disabled.toSet(),
): List<ProviderListAction> {
    if (provider.id in disabled) return listOf(ProviderListAction.ENABLE)
    if (configured(provider, state, state.connected.toSet())) return listOf(ProviderListAction.DISCONNECT)
    val methods = providerMethods(provider, state)
    return buildList {
        if (methods.any { it.type == "oauth" }) add(ProviderListAction.OAUTH)
        if (methods.any { it.type == "api" }) add(ProviderListAction.CONNECT)
    }
}
