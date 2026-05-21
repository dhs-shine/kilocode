package ai.kilocode.backend.migration

import kotlinx.serialization.json.JsonElement

/**
 * Source adapter for legacy Kilo Code v5.x data.
 *
 * Abstracts over VS Code SecretStorage, globalState, and filesystem access.
 * Callers supply raw content; this interface never reads VS Code storage directly.
 * The store also persists the migration status key ("kilo.legacyMigrationStatus").
 */
interface LegacyMigrationStore {
    fun status(): LegacyMigrationStatus?
    fun mark(status: LegacyMigrationStatus)

    /** Raw JSON from "roo_cline_config_api_config" secret */
    fun providerProfilesRaw(): String?
    /** Raw JSON from an OAuth secret key (e.g. openai-codex-oauth-credentials) */
    fun oauthRaw(key: String): String?
    /** Raw JSON from mcp_settings.json */
    fun mcpSettingsRaw(): String?
    /** Raw YAML or JSON from custom_modes.yaml */
    fun customModesRaw(): String?
    /** Raw JSON from customModePrompts globalState key */
    fun customModePromptsRaw(): String?
    /** Raw JSON from ghostServiceSettings globalState key */
    fun autocompleteRaw(): String?
    /** Value from a globalState key */
    fun globalStateValue(key: String): JsonElement?
    /** Raw JSON array from "taskHistory" globalState key */
    fun taskHistoryRaw(): String?
    /** Raw JSON array from tasks/<id>/api_conversation_history.json */
    fun taskConversationRaw(id: String): String?

    fun cleanup(targets: LegacyCleanupTargets): LegacyCleanupReport
}

/**
 * In-memory store for unit tests and future UI/import flows.
 *
 * Accepts raw strings/values that callers supply programmatically.
 * Calls to [cleanup] always succeed and return the targets as cleaned.
 */
class InMemoryLegacyMigrationStore : LegacyMigrationStore {
    var migrationStatus: LegacyMigrationStatus? = null
    var providerProfiles: String? = null
    val oauthSecrets: MutableMap<String, String> = mutableMapOf()
    var mcpSettings: String? = null
    var customModes: String? = null
    var customModePrompts: String? = null
    var autocomplete: String? = null
    val globalState: MutableMap<String, JsonElement> = mutableMapOf()
    var taskHistory: String? = null
    val conversations: MutableMap<String, String> = mutableMapOf()

    override fun status() = migrationStatus
    override fun mark(status: LegacyMigrationStatus) { migrationStatus = status }
    override fun providerProfilesRaw() = providerProfiles
    override fun oauthRaw(key: String) = oauthSecrets[key]
    override fun mcpSettingsRaw() = mcpSettings
    override fun customModesRaw() = customModes
    override fun customModePromptsRaw() = customModePrompts
    override fun autocompleteRaw() = autocomplete
    override fun globalStateValue(key: String) = globalState[key]
    override fun taskHistoryRaw() = taskHistory
    override fun taskConversationRaw(id: String) = conversations[id]

    override fun cleanup(targets: LegacyCleanupTargets): LegacyCleanupReport {
        val cleaned = mutableListOf<String>()
        if (targets.providerProfiles) { providerProfiles = null; cleaned.add("providerProfiles") }
        if (targets.mcpSettings) { mcpSettings = null; cleaned.add("mcpSettings") }
        if (targets.customModes) { customModes = null; cleaned.add("customModes") }
        if (targets.globalState) { globalState.clear(); cleaned.add("globalState") }
        if (targets.taskHistory) { taskHistory = null; conversations.clear(); cleaned.add("taskHistory") }
        return LegacyCleanupReport(cleaned = cleaned, errors = emptyList())
    }
}
