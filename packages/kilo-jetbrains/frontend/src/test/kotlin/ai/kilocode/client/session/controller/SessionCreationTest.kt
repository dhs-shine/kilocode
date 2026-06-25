package ai.kilocode.client.session.controller

import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ModelStateDto
import ai.kilocode.rpc.dto.ProviderDto

class SessionCreationTest : SessionControllerTestBase() {

    fun `test prompt creates session on first call`() {
        ready()
        val m = controller()
        val events = collect(m)
        flush()
        events.clear()

        edt { m.prompt("hello") }
        flush()

        assertEquals(1, rpc.creates)
        assertEquals(1, rpc.prompts.size)
        assertEquals("ses_test", rpc.prompts[0].first)
        assertControllerEvents(
            """
            AccountOverlayChanged hide
            ViewChanged session
            """,
            events,
        )
        assertSession(
            """
            [code] [kilo/gpt-5] [idle]
            """,
            m,
        )
    }

    fun `test prompt reuses existing session`() {
        ready()
        val m = controller()
        flush()

        edt { m.prompt("first") }
        flush()
        edt { m.prompt("second") }
        flush()

        assertEquals(1, rpc.creates)
        assertEquals(2, rpc.prompts.size)
        assertEquals("ses_test", rpc.prompts[1].first)
    }

    fun `test same-turn first prompts share session creation`() {
        ready()
        val m = controller()
        flush()

        edt {
            m.prompt("first")
            m.prompt("second")
        }
        flush()

        assertEquals(1, rpc.creates)
        assertEquals(listOf("ses_test", "ses_test"), rpc.prompts.map { it.first })
        assertEquals(listOf("first", "second"), rpc.prompts.map { it.third.parts.single().text.toString() }.sorted())
    }

    fun `test same-turn first prompt and command share session creation`() {
        ready()
        val m = controller()
        flush()

        edt {
            m.prompt("first")
            m.command("deploy", "prod")
        }
        flush()

        assertEquals(1, rpc.creates)
        assertEquals("ses_test", rpc.prompts.single().first)
        assertEquals("ses_test", rpc.commands.single().id)
    }

    fun `test prompt with existing ID skips creation`() {
        ready()
        val m = controller("existing")
        collect(m)
        flush()

        edt { m.prompt("hello") }
        flush()

        assertEquals(0, rpc.creates)
        assertEquals(1, rpc.prompts.size)
        assertEquals("existing", rpc.prompts[0].first)
    }

    fun `test prompt sends selected model agent and variant`() {
        appRpc.models = ModelStateDto(variant = mapOf("kilo/gpt-5" to "medium"))
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "kilo/gpt-5"))
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "medium", "high")),
                    ),
                ),
            ),
        )
        val m = controller("existing")
        collect(m)
        flush()

        edt { m.prompt("hello") }
        flush()

        val prompt = rpc.prompts.single().third
        assertEquals("kilo", prompt.providerID)
        assertEquals("gpt-5", prompt.modelID)
        assertEquals("code", prompt.agent)
        assertEquals("medium", prompt.variant)
    }

    fun `test prompt without selected model does not create session`() {
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = null))
        projectRpc.state.value = workspaceReady()
        val m = controller()
        flush()
        edt { m.model.model = null }

        edt { m.prompt("hello") }
        flush()

        assertEquals(0, rpc.creates)
        assertTrue(rpc.prompts.isEmpty())
        assertSession(
            """
            [code] [error] [Select a model before sending a prompt.]
            """,
            m,
        )
    }

    private fun ready() {
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "kilo/gpt-5"))
        projectRpc.state.value = workspaceReady()
    }
}
