@file:Suppress("UnstableApiUsage")

package ai.kilocode.backend.rpc

import ai.kilocode.backend.app.KiloBackendAppService
import ai.kilocode.backend.migration.KiloBackendLegacyMigrationStoreService
import ai.kilocode.backend.migration.LegacyMigrationResultItem
import ai.kilocode.backend.migration.LegacyMigrationSink
import ai.kilocode.backend.migration.LegacyMigrationStatus
import ai.kilocode.backend.migration.MigrationItemCategory
import ai.kilocode.backend.migration.MigrationItemStatus
import ai.kilocode.rpc.KiloMigrationRpcApi
import ai.kilocode.rpc.dto.LegacyCleanupReportDto
import ai.kilocode.rpc.dto.LegacyCleanupTargetsDto
import ai.kilocode.rpc.dto.LegacyMigrationDetectionDto
import ai.kilocode.rpc.dto.LegacyMigrationEventDto
import ai.kilocode.rpc.dto.LegacyMigrationSelectionsDto
import ai.kilocode.rpc.dto.LegacyMigrationStatusDto
import ai.kilocode.backend.app.KiloBackendMigrationManager
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

class KiloMigrationRpcApiImpl : KiloMigrationRpcApi {

    private val app: KiloBackendAppService get() = service()
    private val storeService: KiloBackendLegacyMigrationStoreService get() = service()

    private fun manager(): KiloBackendMigrationManager {
        val http = app.http ?: throw IllegalStateException("Not connected")
        val port = app.port
        return KiloBackendMigrationManager(http, port)
    }

    override suspend fun status(): LegacyMigrationStatusDto? {
        val mgr = manager()
        val store = storeService.store()
        val status = mgr.status(store) ?: return null
        return MigrationRpcMapper.toDto(status)
    }

    override suspend fun detect(): LegacyMigrationDetectionDto {
        val mgr = manager()
        val store = storeService.store()
        val detection = withContext(Dispatchers.IO) { mgr.detect(store) }
        return MigrationRpcMapper.toDto(detection)
    }

    override suspend fun migrate(selections: LegacyMigrationSelectionsDto): Flow<LegacyMigrationEventDto> {
        val mgr = manager()
        val domainSelections = MigrationRpcMapper.fromDto(selections)
        val store = storeService.store()
        return channelFlow {
            withContext(Dispatchers.IO) {
                val sink = object : LegacyMigrationSink {
                    override fun item(progress: ai.kilocode.backend.migration.LegacyMigrationItemProgress) {
                        trySendBlocking(LegacyMigrationEventDto.Item(MigrationRpcMapper.toDto(progress)))
                    }
                    override fun session(progress: ai.kilocode.backend.migration.LegacyMigrationSessionProgress) {
                        trySendBlocking(LegacyMigrationEventDto.Session(MigrationRpcMapper.toDto(progress)))
                    }
                }
                val report = runCatching {
                    mgr.migrate(store, domainSelections, sink)
                }.getOrElse { e ->
                    val msg = e.message ?: "Migration failed"
                    val errItem = LegacyMigrationResultItem(
                        item = "Migration",
                        category = MigrationItemCategory.settings,
                        status = MigrationItemStatus.error,
                        message = msg,
                    )
                    trySendBlocking(LegacyMigrationEventDto.Complete(listOf(MigrationRpcMapper.toDto(errItem))))
                    return@withContext
                }
                trySendBlocking(LegacyMigrationEventDto.Complete(report.items.map(MigrationRpcMapper::toDto)))
            }
        }
    }

    override suspend fun skip() {
        val mgr = manager()
        val store = storeService.store()
        mgr.mark(store, LegacyMigrationStatus.Skipped)
    }

    override suspend fun finalize(status: LegacyMigrationStatusDto) {
        val mgr = manager()
        val store = storeService.store()
        val domain = MigrationRpcMapper.fromDto(status)
        if (domain == LegacyMigrationStatus.Skipped) return
        mgr.mark(store, domain)
    }

    override suspend fun cleanup(targets: LegacyCleanupTargetsDto): LegacyCleanupReportDto {
        val mgr = manager()
        val store = storeService.store()
        val report = withContext(Dispatchers.IO) { mgr.cleanup(store, MigrationRpcMapper.fromDto(targets)) }
        return MigrationRpcMapper.toDto(report)
    }
}
