package ai.kilocode.backend.migration

import ai.kilocode.backend.cli.KiloBackendCliManager
import ai.kilocode.backend.cli.KiloCliConfigPath
import ai.kilocode.log.KiloLog
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Provides the production [LegacyMigrationStore] backed by the CLI Kilo config directory. */
@Service(Service.Level.APP)
class KiloBackendLegacyMigrationStoreService {

    companion object {
        fun getInstance(): KiloBackendLegacyMigrationStoreService = service()

        internal fun store(log: KiloLog, env: Map<String, String>? = null): LegacyMigrationStore {
            return fileStore(log, env).store
        }

        internal fun status(log: KiloLog, env: Map<String, String>? = null): LegacyMigrationStatus? {
            val file = fileStore(log, env)
            return markerStatus(file.marker)
        }

        internal fun markStatus(log: KiloLog, status: LegacyMigrationStatus, env: Map<String, String>? = null) {
            val file = fileStore(log, env)
            file.marker.parentFile?.mkdirs()
            file.marker.writeText(status.name)
            log.info("Migration status: marked status=$status file=${file.marker.absolutePath}")
        }

        internal fun resetStatus(log: KiloLog, env: Map<String, String>? = null): Boolean {
            val file = fileStore(log, env)
            if (!file.marker.exists()) {
                log.info("Migration status: reset skipped because marker is missing file=${file.marker.absolutePath}")
                return true
            }
            val ok = file.marker.delete()
            log.info("Migration status: reset marker file=${file.marker.absolutePath} deleted=$ok")
            return ok
        }

        internal fun resolveSource(log: KiloLog, includeFile: Boolean = false): LegacyMigrationSource {
            val file = fileStore(log)
            if (includeFile && file.file.isFile) {
                log.info("Migration source: file")
                return LegacyMigrationSource.FileBacked(file.store)
            }
            log.info("Migration source: probing raw v5 data; legacy settings file is input only file=${file.file.absolutePath}")
            val src = LegacyV5Sources(log = log::info)
            if (!src.anyPresent()) {
                log.info("Migration source: none")
                return LegacyMigrationSource.None(file.store)
            }
            val obj = LegacyV5Importer(src).import()
            if (obj.isEmpty()) {
                log.info("Migration source: none")
                return LegacyMigrationSource.None(file.store)
            }
            val store = InMemoryLegacyMigrationStore(obj)
            log.info("Migration source: v5-raw keys=${obj.keys.size} conversations=${(obj["conversations"] as? JsonObject)?.size ?: 0}")
            return LegacyMigrationSource.V5Raw(store, obj, file.file)
        }

        private fun fileStore(log: KiloLog, env: Map<String, String>? = null): FileStore {
            val cfg = env ?: KiloBackendCliManager(log).buildEnv("migration")
            val file = KiloCliConfigPath.legacySettingsFile(cfg)
            val store = LegacySettingsFileMigrationStore(file) { msg, err ->
                if (err == null) log.warn(msg) else log.warn(msg, err)
            }
            return FileStore(file, store)
        }

        private fun markerStatus(file: File): LegacyMigrationStatus? {
            if (!file.isFile) return null
            return runCatching { LegacyMigrationStatus.valueOf(file.readText().trim()) }.getOrNull()
        }

        private data class FileStore(
            val file: File,
            val store: LegacySettingsFileMigrationStore,
        ) {
            val marker = File(file.parentFile, "legacy-migration-status")
        }
    }

    private val log = KiloLog.create(KiloBackendLegacyMigrationStoreService::class.java)

    fun store(): LegacyMigrationStore = store(log)

    fun status(): LegacyMigrationStatus? = status(log)

    fun markStatus(status: LegacyMigrationStatus) = markStatus(log, status)

    fun resetStatus(): Boolean = resetStatus(log)

    fun resolveSource(includeFile: Boolean = false): LegacyMigrationSource = resolveSource(log, includeFile)

}

sealed class LegacyMigrationSource(open val store: LegacyMigrationStore) {
    data class FileBacked(override val store: LegacyMigrationStore) : LegacyMigrationSource(store)
    data class V5Raw(
        override val store: LegacyMigrationStore,
        val consolidated: JsonObject,
        val file: File,
    ) : LegacyMigrationSource(store)
    data class None(override val store: LegacyMigrationStore) : LegacyMigrationSource(store)
}

class LegacySettingsFileMigrationStore(
    private val file: File,
    private val warn: (String, Throwable?) -> Unit = { _, _ -> },
) : LegacyMigrationStore {
    companion object {
        internal val json = Json { prettyPrint = true }
        private const val STATUS = "migrationStatus"
    }

    override fun status(): LegacyMigrationStatus? {
        val raw = read()?.get(STATUS)?.jsonPrimitive?.content ?: return null
        return runCatching { LegacyMigrationStatus.valueOf(raw) }.getOrNull()
    }

    override fun mark(status: LegacyMigrationStatus) {
        val root = read().orEmpty().toMutableMap()
        root[STATUS] = JsonPrimitive(status.name)
        write(JsonObject(root))
    }

    override fun providerProfilesRaw(): String? = string("providerProfiles")
    override fun oauthRaw(key: String): String? = (read()?.get("oauth") as? JsonObject)?.get(key)?.jsonPrimitive?.content
    override fun mcpSettingsRaw(): String? = string("mcpSettings")
    override fun customModesRaw(): String? = string("customModes")
    override fun customModePromptsRaw(): String? = string("customModePrompts")
    override fun autocompleteRaw(): String? = string("autocomplete")
    override fun globalStateValue(key: String): JsonElement? = (read()?.get("globalState") as? JsonObject)?.get(key)
    override fun taskHistoryRaw(): String? = string("taskHistory")
    override fun taskConversationRaw(id: String): String? = (read()?.get("conversations") as? JsonObject)?.get(id)?.jsonPrimitive?.content

    override fun cleanup(targets: LegacyCleanupTargets): LegacyCleanupReport {
        val root = read()?.toMutableMap() ?: return LegacyCleanupReport(cleaned = emptyList(), errors = emptyList())
        if (targets.legacySettingsFile) {
            val err = runCatching {
                if (file.delete()) null else "Failed to delete ${file.absolutePath}"
            }.getOrElse { it.message ?: "Failed to delete ${file.absolutePath}" }
            return LegacyCleanupReport(
                cleaned = if (err == null) listOf("legacySettingsFile") else emptyList(),
                errors = listOfNotNull(err),
            )
        }
        val cleaned = mutableListOf<String>()
        if (targets.providerProfiles && root.remove("providerProfiles") != null) cleaned.add("providerProfiles")
        if (targets.mcpSettings && root.remove("mcpSettings") != null) cleaned.add("mcpSettings")
        if (targets.customModes && root.remove("customModes") != null) cleaned.add("customModes")
        if (targets.globalState && root.remove("globalState") != null) cleaned.add("globalState")
        if (targets.taskHistory) {
            val history = root.remove("taskHistory") != null
            val conv = root.remove("conversations") != null
            if (history || conv) cleaned.add("taskHistory")
        }
        val err = runCatching { write(JsonObject(root)) }.exceptionOrNull()?.message
        return LegacyCleanupReport(cleaned = if (err == null) cleaned else emptyList(), errors = listOfNotNull(err))
    }

    private fun string(key: String): String? = read()?.get(key)?.jsonPrimitive?.content

    private fun read(): JsonObject? {
        if (!file.isFile) return null
        return try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: SerializationException) {
            warn("Malformed legacy migration settings at ${file.absolutePath}", e)
            null
        } catch (e: IllegalArgumentException) {
            warn("Malformed legacy migration settings at ${file.absolutePath}", e)
            null
        }
    }

    private fun write(root: JsonObject) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(JsonObject.serializer(), root))
    }
}

fun materializeLegacyMigrationSource(
    source: LegacyMigrationSource,
    log: KiloLog? = null,
): LegacyMigrationStore = when (source) {
    is LegacyMigrationSource.FileBacked -> source.store
    is LegacyMigrationSource.None -> source.store
    is LegacyMigrationSource.V5Raw -> {
        source.file.parentFile?.mkdirs()
        log?.info("Migration source: writing regenerated legacy settings JSON file=${source.file.absolutePath}")
        source.file.writeText(LegacySettingsFileMigrationStore.json.encodeToString(JsonObject.serializer(), source.consolidated))
        log?.info("Migration source: regenerated legacy settings JSON file=${source.file.absolutePath}")
        LegacySettingsFileMigrationStore(source.file)
    }
}
