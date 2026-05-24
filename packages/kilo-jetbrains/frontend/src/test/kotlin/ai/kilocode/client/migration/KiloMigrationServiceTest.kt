package ai.kilocode.client.migration

import ai.kilocode.client.testing.FakeMigrationRpcApi
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
import kotlinx.coroutines.runBlocking

@Suppress("UnstableApiUsage")
class KiloMigrationServiceTest : BasePlatformTestCase() {

    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeMigrationRpcApi
    private lateinit var service: KiloMigrationService

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
        rpc = FakeMigrationRpcApi()
        service = KiloMigrationService(scope, rpc)
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

    fun `test check calls status before detect`() {
        rpc.statusResult = null
        rpc.detectResult = FakeMigrationRpcApi.emptyDetection()
        service.check()
        settle()
        assertEquals(1, rpc.statusCalls.size)
        assertEquals(1, rpc.detectCalls.size)
    }

    fun `test existing status hides state and does not call detect`() {
        rpc.statusResult = LegacyMigrationStatusDto.completed
        service.check()
        settle()
        assertEquals(1, rpc.statusCalls.size)
        assertEquals(0, rpc.detectCalls.size)
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test no data hides state`() {
        rpc.statusResult = null
        rpc.detectResult = FakeMigrationRpcApi.emptyDetection()
        service.check()
        settle()
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test detected data sets needed state`() {
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
        settle()
        assertTrue("state should be Needed", service.state.value is MigrationUiState.Needed)
    }

    fun `test duplicate check while in flight makes one rpc call`() {
        rpc.statusResult = null
        rpc.detectResult = FakeMigrationRpcApi.emptyDetection()
        service.check()
        service.check()
        settle()
        // Due to in-flight guard only one pair of calls should happen
        assertEquals(1, rpc.statusCalls.size)
    }

    fun `test skip marks status and hides`() {
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
        settle()
        service.skip()
        settle()
        assertEquals(1, rpc.skipCalls.size)
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test finish calls finalize and hides`() {
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
        settle()
        service.finish()
        settle()
        assertEquals(1, rpc.finalizeCalls.size)
        assertEquals(LegacyMigrationStatusDto.completed, rpc.finalizeCalls[0])
        assertEquals(MigrationUiState.Hidden, service.state.value)
    }

    fun `test start emits migrating state and initial pending progress`() = runBlocking {
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
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
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
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
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
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
        rpc.statusResult = null
        rpc.detectResult = sampleDetection()
        service.check()
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
