@file:Suppress("UnstableApiUsage")

package ai.kilocode.client.migration

import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.KiloMigrationRpcApi
import ai.kilocode.rpc.dto.LegacyMigrationEventDto
import ai.kilocode.rpc.dto.LegacyMigrationResultItemDto
import ai.kilocode.rpc.dto.LegacyMigrationStatusDto
import ai.kilocode.rpc.dto.MigrationItemCategoryDto
import ai.kilocode.rpc.dto.MigrationItemProgressStatusDto
import ai.kilocode.rpc.dto.MigrationItemStatusDto
import ai.kilocode.rpc.dto.MigrationSessionPhaseDto
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Interface exposed to session UI components.
 */
interface MigrationUiController {
    val state: StateFlow<MigrationUiState>
    fun check()
    fun start(selections: MigrationUiSelections)
    fun force(ids: List<String>)
    fun skip()
    fun finish()
}

/**
 * App-level service that manages migration wizard state shared across all session UIs.
 *
 * Detects and runs legacy migration via [KiloMigrationRpcApi].
 * All Swing interactions must happen on EDT; service coroutines run off EDT.
 */
@Service(Service.Level.APP)
class KiloMigrationService internal constructor(
    private val cs: CoroutineScope,
    private val rpc: KiloMigrationRpcApi?,
) : MigrationUiController {

    /** Platform constructor — resolves RPC lazily. */
    constructor(cs: CoroutineScope) : this(cs, null)

    companion object {
        private val LOG = KiloLog.create(KiloMigrationService::class.java)

        fun getInstance(): KiloMigrationService = service()
    }

    private val _state = MutableStateFlow<MigrationUiState>(MigrationUiState.Hidden)
    override val state: StateFlow<MigrationUiState> = _state.asStateFlow()

    private val checking = AtomicBoolean(false)
    private val migrating = AtomicBoolean(false)
    private val migrateJob = AtomicReference<Job?>(null)

    // ------ RPC helper ------

    private suspend fun <T> call(block: suspend KiloMigrationRpcApi.() -> T): T {
        val api = rpc
        return if (api != null) block(api) else durable { block(KiloMigrationRpcApi.getInstance()) }
    }

    // ------ MigrationUiController ------

    /**
     * Check if migration is needed. Idempotent and in-flight guarded.
     * Calls status first; if status exists, hides. Then calls detect; if no data, hides.
     * Detection failures log and leave state unchanged.
     */
    override fun check() {
        if (!checking.compareAndSet(false, true)) return
        cs.launch {
            try {
                val status = try {
                    call { status() }
                } catch (e: Exception) {
                    LOG.warn("migration status check failed", e)
                    checking.set(false)
                    return@launch
                }
                if (status != null) {
                    _state.value = MigrationUiState.Hidden
                    checking.set(false)
                    return@launch
                }
                val detection = try {
                    call { detect() }
                } catch (e: Exception) {
                    LOG.warn("migration detect failed", e)
                    checking.set(false)
                    return@launch
                }
                _state.value = if (detection.hasData) MigrationUiState.Needed(detection) else MigrationUiState.Hidden
            } finally {
                checking.set(false)
            }
        }
    }

    /**
     * Start migration for the given user selections.
     */
    override fun start(selections: MigrationUiSelections) {
        val current = _state.value as? MigrationUiState.Needed ?: return
        if (!migrating.compareAndSet(false, true)) return

        val dto = MigrationSelectionBuilder.toDto(selections)
        val initialProgress = buildInitialProgress(selections, current.detection)

        _state.value = current.copy(
            phase = MigrationUiPhase.migrating,
            running = true,
            progress = initialProgress,
            sessionProgress = null,
            sessionSummary = SessionMigrationSummary(),
            results = emptyList(),
        )

        val job = cs.launch {
            try {
                val flow = try {
                    call { migrate(dto) }
                } catch (e: Exception) {
                    LOG.warn("migration start failed", e)
                    finishWithError(e.message ?: "Migration failed")
                    return@launch
                }
                flow.collect { event -> handleEvent(event) }
            } finally {
                migrating.set(false)
            }
        }
        migrateJob.set(job)
    }

    /**
     * Force re-import selected sessions (skipped sessions).
     */
    override fun force(ids: List<String>) {
        val current = _state.value as? MigrationUiState.Needed ?: return
        if (!migrating.compareAndSet(false, true)) return

        val dto = MigrationSelectionBuilder.forceSessionsDto(ids)
        val initialProgress = ids.map {
            MigrationItemUiProgress(it, MigrationItemCategoryDto.session, MigrationItemProgressStatusDto.migrating)
        }

        // Keep non-session results, reset session progress
        val nonSession = current.progress.filter { it.category != MigrationItemCategoryDto.session }
        _state.value = current.copy(
            phase = MigrationUiPhase.migrating,
            running = true,
            progress = nonSession + initialProgress,
            sessionProgress = null,
            sessionSummary = SessionMigrationSummary(),
        )

        val job = cs.launch {
            try {
                val flow = try {
                    call { migrate(dto) }
                } catch (e: Exception) {
                    LOG.warn("force migration start failed", e)
                    finishWithError(e.message ?: "Migration failed")
                    return@launch
                }
                flow.collect { event -> handleEvent(event) }
            } finally {
                migrating.set(false)
            }
        }
        migrateJob.set(job)
    }

    /**
     * Skip migration — marks status and hides for all observers.
     */
    override fun skip() {
        cs.launch {
            try {
                call { skip() }
            } catch (e: Exception) {
                LOG.warn("migration skip failed", e)
            }
            _state.value = MigrationUiState.Hidden
        }
    }

    /**
     * Finalize migration — marks completed/completed_with_errors and hides.
     */
    override fun finish() {
        val current = _state.value as? MigrationUiState.Needed ?: run {
            _state.value = MigrationUiState.Hidden
            return
        }
        val hasErrors = current.results.any { it.status == MigrationItemStatusDto.error }
        val status = if (hasErrors) LegacyMigrationStatusDto.completed_with_errors else LegacyMigrationStatusDto.completed
        cs.launch {
            try {
                call { finalize(status) }
            } catch (e: Exception) {
                LOG.warn("migration finalize failed", e)
            }
            _state.value = MigrationUiState.Hidden
        }
    }

    // ------ Internal event handling ------

    private fun handleEvent(event: LegacyMigrationEventDto) {
        val current = _state.value as? MigrationUiState.Needed ?: return
        when (event) {
            is LegacyMigrationEventDto.Item -> {
                val p = event.progress
                val updated = current.progress.map {
                    if (it.item == p.item) it.copy(status = p.status, message = p.message) else it
                }
                _state.value = current.copy(progress = updated)
            }
            is LegacyMigrationEventDto.Session -> {
                val sp = event.progress
                val phase = sp.phase

                // Update session summary buckets
                val summary = when (phase) {
                    MigrationSessionPhaseDto.done -> {
                        val item = LegacyMigrationResultItemDto(
                            item = sp.session?.id ?: "",
                            category = ai.kilocode.rpc.dto.MigrationItemCategoryDto.session,
                            status = MigrationItemStatusDto.success,
                        )
                        current.sessionSummary.copy(imported = current.sessionSummary.imported + item)
                    }
                    MigrationSessionPhaseDto.skipped -> {
                        val item = LegacyMigrationResultItemDto(
                            item = sp.session?.id ?: "",
                            category = ai.kilocode.rpc.dto.MigrationItemCategoryDto.session,
                            status = MigrationItemStatusDto.success,
                            message = "skipped",
                        )
                        current.sessionSummary.copy(skipped = current.sessionSummary.skipped + item)
                    }
                    MigrationSessionPhaseDto.error -> {
                        val item = LegacyMigrationResultItemDto(
                            item = sp.session?.id ?: "",
                            category = ai.kilocode.rpc.dto.MigrationItemCategoryDto.session,
                            status = MigrationItemStatusDto.error,
                            message = sp.error,
                        )
                        current.sessionSummary.copy(errored = current.sessionSummary.errored + item)
                    }
                    else -> current.sessionSummary
                }
                _state.value = current.copy(sessionProgress = sp, sessionSummary = summary)
            }
            is LegacyMigrationEventDto.Complete -> {
                val items = event.items
                val hasErrors = items.any { it.status == MigrationItemStatusDto.error }
                val phase = if (hasErrors) MigrationUiPhase.error else MigrationUiPhase.done
                _state.value = current.copy(
                    running = false,
                    phase = phase,
                    results = items,
                )
            }
            is LegacyMigrationEventDto.Error -> {
                finishWithError(event.message)
            }
        }
    }

    private fun finishWithError(msg: String) {
        val current = _state.value as? MigrationUiState.Needed ?: return
        val errItem = LegacyMigrationResultItemDto(
            item = "Migration",
            category = ai.kilocode.rpc.dto.MigrationItemCategoryDto.settings,
            status = MigrationItemStatusDto.error,
            message = msg,
        )
        _state.value = current.copy(
            running = false,
            phase = MigrationUiPhase.error,
            results = listOf(errItem),
        )
    }

    private fun buildInitialProgress(
        selections: MigrationUiSelections,
        detection: ai.kilocode.rpc.dto.LegacyMigrationDetectionDto,
    ): List<MigrationItemUiProgress> {
        val list = mutableListOf<MigrationItemUiProgress>()
        for (id in selections.providers) {
            list.add(MigrationItemUiProgress(id, MigrationItemCategoryDto.provider))
        }
        for (name in selections.mcpServers) {
            list.add(MigrationItemUiProgress(name, MigrationItemCategoryDto.mcpServer))
        }
        for (slug in selections.customModes) {
            val info = detection.customModes.find { it.slug == slug }
            list.add(MigrationItemUiProgress(info?.name ?: slug, MigrationItemCategoryDto.customMode))
        }
        for (id in selections.sessions) {
            list.add(MigrationItemUiProgress(id, MigrationItemCategoryDto.session))
        }
        if (selections.defaultModel) {
            list.add(MigrationItemUiProgress("Default model", MigrationItemCategoryDto.defaultModel))
        }
        // Settings sub-items
        val ap = selections.settings.autoApproval
        if (ap.commandRules) list.add(MigrationItemUiProgress("Command rules", MigrationItemCategoryDto.settings))
        if (ap.readPermission) list.add(MigrationItemUiProgress("Read permission", MigrationItemCategoryDto.settings))
        if (ap.writePermission) list.add(MigrationItemUiProgress("Write permission", MigrationItemCategoryDto.settings))
        if (ap.executePermission) list.add(MigrationItemUiProgress("Execute permission", MigrationItemCategoryDto.settings))
        if (ap.mcpPermission) list.add(MigrationItemUiProgress("MCP permission", MigrationItemCategoryDto.settings))
        if (ap.taskPermission) list.add(MigrationItemUiProgress("Task permission", MigrationItemCategoryDto.settings))
        if (selections.settings.language) list.add(MigrationItemUiProgress("Language preference", MigrationItemCategoryDto.settings))
        if (selections.settings.autocomplete) list.add(MigrationItemUiProgress("Autocomplete settings", MigrationItemCategoryDto.settings))
        return list
    }
}
