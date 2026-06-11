package ai.kilocode.backend.provider

import ai.kilocode.backend.app.KiloAppState
import ai.kilocode.backend.app.KiloBackendAppService
import ai.kilocode.backend.testing.FakeCliServer
import ai.kilocode.backend.testing.MockCliServer
import ai.kilocode.backend.testing.TestLog
import ai.kilocode.rpc.dto.ProviderDisconnectDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KiloBackendProviderSettingsManagerTest {

    private val mock = MockCliServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        mock.close()
    }

    @Test
    fun `disconnecting available catalog provider returns error without mutation`() = runBlocking {
        mock.providers = """{
            "all":[{"id":"cloudflare-ai-gateway","name":"Cloudflare AI Gateway","source":"custom","models":{}}],
            "default":{},
            "connected":[],
            "failed":[]
        }""".trimIndent()
        mock.providerAuth = """{"cloudflare-ai-gateway":[{"type":"api","label":"API key"}]}"""
        val manager = manager()

        mock.resetCounts()
        val result = manager.disconnect(ProviderDisconnectDto("/test", "cloudflare-ai-gateway"))

        assertEquals("Provider is not connected.", result.error)
        assertNull(mock.lastConfigPatchBody)
        assertNull(mock.lastAuthDeletePath)
        assertEquals(0, mock.requestCount("/global/dispose"))
    }

    @Test
    fun `disconnecting openai compatible custom provider deletes config and auth`() = runBlocking {
        mock.config = """{
            "model":"test/model",
            "provider":{
                "local-openai":{"name":"Local OpenAI","npm":"@ai-sdk/openai-compatible","options":{"baseURL":"http://localhost:11434"}}
            }
        }""".trimIndent()
        mock.providers = """{
            "all":[{"id":"local-openai","name":"Local OpenAI","source":"config","models":{}}],
            "default":{},
            "connected":["local-openai"],
            "failed":[]
        }""".trimIndent()
        val manager = manager()

        mock.resetCounts()
        val result = manager.disconnect(ProviderDisconnectDto("/test", "local-openai"))

        assertNull(result.error)
        assertContains(mock.lastConfigPatchBody.orEmpty(), "\"local-openai\":null")
        assertEquals("/auth/local-openai", mock.lastAuthDeletePath)
        assertEquals(1, mock.requestCount("/global/dispose"))
    }

    private suspend fun manager(): KiloBackendProviderSettingsManager {
        val app = KiloBackendAppService.create(scope, FakeCliServer(mock), TestLog())
        app.connect()
        withTimeout(10_000) {
            app.appState.first { it is KiloAppState.Ready }
        }
        return KiloBackendProviderSettingsManager(app)
    }
}
