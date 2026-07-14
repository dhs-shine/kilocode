package ai.kilocode.backend.migration

import ai.kilocode.backend.testing.TestLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Write-on-migrate: materializing a V5Raw source writes the consolidated legacy-settings.json,
 * and finalizing then records a durable status marker that survives.
 */
class LegacyMigrationMaterializeTest {

    @Test
    fun `materialize v5 raw writes legacy settings file and finalize marks status`() {
        val dir = Files.createTempDirectory("kilo-migration-config").toFile()
        val env = mapOf("KILO_CONFIG_DIR" to dir.absolutePath)
        val log = TestLog()
        val file = dir.resolve("legacy-settings.json")

        val consolidated = buildJsonObject {
            put("providerProfiles", "{\"currentApiConfigName\":\"p\",\"apiConfigs\":{}}")
        }
        val source = LegacyMigrationSource.V5Raw(InMemoryLegacyMigrationStore(consolidated), consolidated, file)

        val store = materializeLegacyMigrationSource(source, log)
        assertTrue(file.isFile)
        val perms = runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull()
        if (perms != null) assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
        assertEquals("{\"currentApiConfigName\":\"p\",\"apiConfigs\":{}}", store.providerProfilesRaw())

        // Re-opening the freshly written file yields the same payload.
        val reopened = LegacySettingsFileMigrationStore(file)
        assertEquals("{\"currentApiConfigName\":\"p\",\"apiConfigs\":{}}", reopened.providerProfilesRaw())

        // Finalizing writes the durable marker and status() honors it afterwards.
        KiloBackendLegacyMigrationStoreService.markStatus(log, LegacyMigrationStatus.Completed, env)
        assertEquals(LegacyMigrationStatus.Completed, KiloBackendLegacyMigrationStoreService.status(log, env))
    }
}
