package ai.kilocode.backend.migration

import ai.kilocode.backend.testing.TestLog
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KiloBackendLegacyMigrationStoreServiceTest {
    @Test
    fun `status marker survives deleted legacy settings file`() {
        val dir = Files.createTempDirectory("kilo-migration-config").toFile()
        val env = mapOf("KILO_CONFIG_DIR" to dir.absolutePath)
        val log = TestLog()
        val store = KiloBackendLegacyMigrationStoreService.store(log, env)
        store.mark(LegacyMigrationStatus.CompletedWithErrors)
        store.cleanup(LegacyCleanupTargets(legacySettingsFile = true))
        KiloBackendLegacyMigrationStoreService.markStatus(log, LegacyMigrationStatus.Completed, env)

        assertFalse(dir.resolve("legacy-settings.json").exists())
        assertEquals(LegacyMigrationStatus.Completed, KiloBackendLegacyMigrationStoreService.status(log, env))
    }

    @Test
    fun `stale inline completed status is ignored without durable marker`() {
        val dir = Files.createTempDirectory("kilo-migration-config").toFile()
        val env = mapOf("KILO_CONFIG_DIR" to dir.absolutePath)
        val log = TestLog()
        val store = KiloBackendLegacyMigrationStoreService.store(log, env)
        store.mark(LegacyMigrationStatus.Completed)

        assertNull(KiloBackendLegacyMigrationStoreService.status(log, env))
    }

    @Test
    fun `inline skipped status is ignored without durable marker`() {
        val dir = Files.createTempDirectory("kilo-migration-config").toFile()
        val env = mapOf("KILO_CONFIG_DIR" to dir.absolutePath)
        val log = TestLog()
        val store = KiloBackendLegacyMigrationStoreService.store(log, env)
        store.mark(LegacyMigrationStatus.Skipped)

        assertNull(KiloBackendLegacyMigrationStoreService.status(log, env))
    }

    @Test
    fun `durable completed status is honored while legacy source payload remains`() {
        val dir = Files.createTempDirectory("kilo-migration-config").toFile()
        val env = mapOf("KILO_CONFIG_DIR" to dir.absolutePath)
        val log = TestLog()
        val store = KiloBackendLegacyMigrationStoreService.store(log, env)
        store.mark(LegacyMigrationStatus.Completed)
        dir.resolve("legacy-settings.json").writeText(
            """{"migrationStatus":"Completed","providerProfiles":"{\"currentApiConfigName\":\"p\",\"apiConfigs\":{}}"}"""
        )
        KiloBackendLegacyMigrationStoreService.markStatus(log, LegacyMigrationStatus.Completed, env)

        assertEquals(LegacyMigrationStatus.Completed, KiloBackendLegacyMigrationStoreService.status(log, env))
    }

    @Test
    fun `reset status deletes durable marker`() {
        val dir = Files.createTempDirectory("kilo-migration-config").toFile()
        val env = mapOf("KILO_CONFIG_DIR" to dir.absolutePath)
        val log = TestLog()
        KiloBackendLegacyMigrationStoreService.markStatus(log, LegacyMigrationStatus.Completed, env)

        assertEquals(LegacyMigrationStatus.Completed, KiloBackendLegacyMigrationStoreService.status(log, env))
        assertEquals(true, KiloBackendLegacyMigrationStoreService.resetStatus(log, env))
        assertNull(KiloBackendLegacyMigrationStoreService.status(log, env))
    }
}
