package ai.kilocode.client.migration

import ai.kilocode.client.testing.FakeMigrationRpcApi
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.LegacyMigrationDetectionDto
import ai.kilocode.rpc.dto.LegacyMigrationEventDto
import ai.kilocode.rpc.dto.LegacyMigrationResultItemDto
import ai.kilocode.rpc.dto.LegacyMigrationStatusDto
import ai.kilocode.rpc.dto.MigrationItemCategoryDto
import ai.kilocode.rpc.dto.MigrationItemProgressStatusDto
import ai.kilocode.rpc.dto.MigrationItemStatusDto
import ai.kilocode.rpc.dto.MigrationProviderInfoDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

@Suppress("UnstableApiUsage")
class KiloMigrationServiceTest : BasePlatformTestCase() {

    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeMigrationRpcApi
    private lateinit var service: KiloMigrationService
    private lateinit var app: MutableStateFlow<KiloAppStateDto>

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        rpc = FakeMigrationRpcApi()
        app = MutableStateFlow(KiloAppStateDto(KiloAppStatusDto.DISCONNECTED))
        service = KiloMigrationService(scope, rpc, app)
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    private fun settle() = runBlocking {
        repeat(3) {
            delay(50)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    fun `test migration required app state shows needed without polling`() {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        settle()
        assertEquals(0, rpc.statusCalls.size)
        assertEquals(0, rpc.detectCalls.size)
        assertTrue("state should be Needed", service.state.value is MigrationUiState.Needed)
    }

    fun `test ready app state hides migration`() {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        settle()
        app.value = KiloAppStateDto(KiloAppStatusDto.READY)
        settle()
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test duplicate migration required does not reset running migration`() {
        val detection = sampleDetection()
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = detection)
        settle()
        service.start(MigrationUiSelections(providers = listOf("profile1")))
        settle()
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = detection)
        settle()
        val state = service.state.value as MigrationUiState.Needed
        assertEquals(MigrationUiPhase.migrating, state.phase)
    }

    fun `test skip marks status and hides`() {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        settle()
        service.skip()
        settle()
        assertEquals(1, rpc.skipCalls.size)
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test finish calls finalize and hides`() {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        settle()
        service.finish()
        settle()
        assertEquals(1, rpc.finalizeCalls.size)
        assertEquals(LegacyMigrationStatusDto.completed, rpc.finalizeCalls[0])
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test start emits migrating state and initial pending progress`() = runBlocking {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        delay(100)
        UIUtil.dispatchAllInvocationEvents()

        val selections = MigrationUiSelections(providers = listOf("profile1"))
        service.start(selections)
        delay(50)
        UIUtil.dispatchAllInvocationEvents()

        val state = service.state.value
        assertTrue("should be Needed after start", state is MigrationUiState.Needed)
        val needed = state as MigrationUiState.Needed
        assertEquals(MigrationUiPhase.migrating, needed.phase)
        assertTrue(needed.running)
        assertTrue("should have initial progress entries", needed.progress.isNotEmpty())
    }

    fun `test complete event without errors sets done phase`() = runBlocking {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        delay(100)
        UIUtil.dispatchAllInvocationEvents()

        val selections = MigrationUiSelections(providers = listOf("profile1"))
        service.start(selections)
        delay(50)
        UIUtil.dispatchAllInvocationEvents()

        val items = listOf(LegacyMigrationResultItemDto("profile1", MigrationItemCategoryDto.provider, MigrationItemStatusDto.success))
        rpc.events.emit(LegacyMigrationEventDto.Complete(items))
        delay(100)
        UIUtil.dispatchAllInvocationEvents()

        val state = service.state.value as? MigrationUiState.Needed
        assertNotNull(state)
        assertEquals(MigrationUiPhase.done, state!!.phase)
        assertFalse(state.running)
    }

    fun `test complete event with errors sets error phase`() = runBlocking {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        delay(100)
        UIUtil.dispatchAllInvocationEvents()

        val selections = MigrationUiSelections(providers = listOf("profile1"))
        service.start(selections)
        delay(50)
        UIUtil.dispatchAllInvocationEvents()

        val items = listOf(LegacyMigrationResultItemDto("profile1", MigrationItemCategoryDto.provider, MigrationItemStatusDto.error, "bad key"))
        rpc.events.emit(LegacyMigrationEventDto.Complete(items))
        delay(100)
        UIUtil.dispatchAllInvocationEvents()

        val state = service.state.value as? MigrationUiState.Needed
        assertNotNull(state)
        assertEquals(MigrationUiPhase.error, state!!.phase)
    }

    fun `test force sends only session selections with force true`() = runBlocking {
        app.value = KiloAppStateDto(KiloAppStatusDto.MIGRATION_REQUIRED, migration = sampleDetection())
        delay(100)
        UIUtil.dispatchAllInvocationEvents()

        service.force(listOf("ses_1", "ses_2"))
        delay(50)
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(1, rpc.migrateCalls.size)
        val dto = rpc.migrateCalls[0]
        assertEquals(emptyList<String>(), dto.providers)
        assertEquals(2, dto.sessions.size)
        assertTrue(dto.sessions.all { it.force })
        assertEquals(listOf("ses_1", "ses_2"), dto.sessions.map { it.id })
    }

    private fun sampleDetection() = LegacyMigrationDetectionDto(
        providers = listOf(
            MigrationProviderInfoDto("profile1", "anthropic", "claude-3", true, true, "anthropic"),
        ),
        mcpServers = emptyList(),
        customModes = emptyList(),
        sessions = emptyList(),
        defaultModel = null,
        settings = null,
        hasData = true,
    )
}
