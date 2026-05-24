package ai.kilocode.backend.migration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * Provides the production [LegacyMigrationStore] for use by the migration RPC implementation.
 *
 * Status persistence uses [PropertiesComponent] (app-level JetBrains persistent store).
 * Raw legacy source acquisition is not yet implemented; the store returns null for all
 * data accessors, so [LegacyMigrationEngine.detect] will report hasData=false and the
 * migration wizard will remain hidden until a real source adapter is plugged in.
 */
@Service(Service.Level.APP)
class KiloBackendLegacyMigrationStoreService {

    companion object {
        private const val STATUS_KEY = "kilo.legacyMigrationStatus"

        fun getInstance(): KiloBackendLegacyMigrationStoreService = service()
    }

    fun store(): LegacyMigrationStore = PersistentStatusStore()

    private inner class PersistentStatusStore : LegacyMigrationStore {
        override fun status(): LegacyMigrationStatus? {
            val raw = PropertiesComponent.getInstance().getValue(STATUS_KEY) ?: return null
            return runCatching { LegacyMigrationStatus.valueOf(raw) }.getOrNull()
        }

        override fun mark(status: LegacyMigrationStatus) {
            PropertiesComponent.getInstance().setValue(STATUS_KEY, status.name)
        }

        // Legacy source adapters — not yet implemented; return null to suppress migration UI.
        override fun providerProfilesRaw(): String? = null
        override fun oauthRaw(key: String): String? = null
        override fun mcpSettingsRaw(): String? = null
        override fun customModesRaw(): String? = null
        override fun customModePromptsRaw(): String? = null
        override fun autocompleteRaw(): String? = null
        override fun globalStateValue(key: String) = null
        override fun taskHistoryRaw(): String? = null
        override fun taskConversationRaw(id: String): String? = null

        override fun cleanup(targets: LegacyCleanupTargets): LegacyCleanupReport =
            LegacyCleanupReport(cleaned = emptyList(), errors = emptyList())
    }
}
