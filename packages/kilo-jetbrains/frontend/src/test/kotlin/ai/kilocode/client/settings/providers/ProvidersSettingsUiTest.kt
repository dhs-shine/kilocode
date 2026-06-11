package ai.kilocode.client.settings.providers

import ai.kilocode.rpc.dto.CustomProviderConfigDto
import ai.kilocode.rpc.dto.ProviderAuthMethodDto
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JComponent

class ProvidersSettingsUiTest : BasePlatformTestCase() {

    fun `test catalog provider without auth methods is connectable`() {
        val content = content()

        content.update(
            ProviderSettingsDto(
                providers = listOf(provider("models-dev-provider", "Models Dev Provider", source = "custom")),
            ),
        )

        val labels = buttons(content)
        assertTrue(labels.contains("Connect"))
        assertFalse(labels.contains("Disconnect"))
    }

    fun `test catalog custom provider is connectable not disconnectable`() {
        val content = content()

        content.update(
            ProviderSettingsDto(
                providers = listOf(provider("cloudflare-ai-gateway", "Cloudflare AI Gateway", source = "custom")),
                auth = mapOf(
                    "cloudflare-ai-gateway" to listOf(
                        ProviderAuthMethodDto("api", "API key"),
                        ProviderAuthMethodDto("oauth", "OAuth"),
                    ),
                ),
            ),
        )

        val labels = buttons(content)
        assertTrue(labels.contains("Connect"))
        assertTrue(labels.contains("OAuth"))
        assertFalse(labels.contains("Disconnect"))
    }

    fun `test configured custom provider is disconnectable`() {
        val content = content()

        content.update(
            ProviderSettingsDto(
                providers = listOf(provider("local-openai", "Local OpenAI", source = "custom")),
                config = mapOf("local-openai" to CustomProviderConfigDto("local-openai", npm = "@ai-sdk/openai-compatible")),
                auth = mapOf("local-openai" to listOf(ProviderAuthMethodDto("api", "API key"))),
            ),
        )

        assertEquals(listOf("Disconnect"), buttons(content).filter { it in setOf("Connect", "OAuth", "Disconnect") })
    }

    private fun content() = ProvidersContent({}, {}, {}, {}, {}, {})

    private fun provider(id: String, name: String, source: String) = ProviderSettingsProviderDto(
        id = id,
        name = name,
        source = source,
    )

    private fun buttons(component: JComponent): List<String> = components(component)
        .filterIsInstance<AbstractButton>()
        .map { it.text }

    private fun components(component: JComponent): List<java.awt.Component> {
        val out = mutableListOf<java.awt.Component>()
        fun visit(c: java.awt.Component) {
            out += c
            if (c is Container) c.components.forEach { visit(it) }
        }
        visit(component)
        return out
    }
}
