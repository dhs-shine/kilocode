package ai.kilocode.client.settings.providers

import ai.kilocode.client.app.KiloProviderService
import ai.kilocode.client.testing.FakeProviderRpcApi
import ai.kilocode.rpc.dto.CustomProviderConfigDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ProviderAuthMethodDto
import ai.kilocode.rpc.dto.ProviderDisconnectDto
import ai.kilocode.rpc.dto.ProviderMetadataDto
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.UIManager

@Suppress("UNCHECKED_CAST")
class ProvidersSettingsUiTest : BasePlatformTestCase() {
    private var scope: CoroutineScope? = null
    private var ui: ProvidersSettingsUi? = null

    override fun tearDown() {
        try {
            val panel = ui
            if (panel != null) edt { panel.dispose() }
            ui = null
            scope?.cancel()
            scope = null
        } finally {
            super.tearDown()
        }
    }

    fun `test catalog provider without auth methods is connectable`() {
        val content = content()

        edt {
            content.update(
                ProviderSettingsDto(
                    providers = listOf(provider("models-dev-provider", "Models Dev Provider")),
                ),
            )
        }

        edt {
            assertEquals(listOf(ProviderListAction.CONNECT), rows(content).single().actions)
            assertFalse(rows(content).single().actions.contains(ProviderListAction.DISCONNECT))
        }
    }

    fun `test provider with api and oauth methods exposes both actions`() {
        val content = content()

        edt {
            content.update(
                ProviderSettingsDto(
                    providers = listOf(provider("cloudflare-ai-gateway", "Cloudflare AI Gateway")),
                    auth = mapOf(
                        "cloudflare-ai-gateway" to listOf(
                            ProviderAuthMethodDto("api", "API key"),
                            ProviderAuthMethodDto("oauth", "OAuth"),
                        ),
                    ),
                ),
            )
        }

        edt { assertEquals(listOf(ProviderListAction.OAUTH, ProviderListAction.CONNECT), rows(content).single().actions) }
    }

    fun `test content uses border layout with toolbar north and list center`() {
        val content = content()
        edt {
            val layout = content.layout as BorderLayout
            val north = layout.getLayoutComponent(BorderLayout.NORTH) as Container
            val center = layout.getLayoutComponent(BorderLayout.CENTER) as Container

            assertEquals(listOf("Refresh"), components(north).filterIsInstance<JButton>().map { it.text })
            assertEquals(1, components(center).filterIsInstance<SearchTextField>().size)
            assertEquals(1, components(center).filterIsInstance<JBList<ProviderListRow>>().size)
        }
    }

    fun `test configured custom provider exposes only disconnect`() {
        val content = content()

        edt {
            content.update(
                ProviderSettingsDto(
                    providers = listOf(provider("local-openai", "Local OpenAI", source = "custom")),
                    config = mapOf("local-openai" to CustomProviderConfigDto("local-openai", npm = "@ai-sdk/openai-compatible")),
                    auth = mapOf("local-openai" to listOf(ProviderAuthMethodDto("api", "API key"))),
                ),
            )
        }

        edt { assertEquals(listOf(ProviderListAction.DISCONNECT), rows(content).single().actions) }
    }

    fun `test popular rows use vscode order including kilo`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(
                    provider("openrouter", "OpenRouter"),
                    provider("kilo", "Kilo"),
                    provider("google", "Google"),
                    provider("anthropic", "Anthropic"),
                    provider("vercel", "Vercel"),
                    provider("openai", "OpenAI"),
                    provider("deepseek", "DeepSeek"),
                ),
            ),
            "",
        )

        assertEquals(listOf("kilo", "anthropic", "deepseek", "openai", "google", "openrouter", "vercel"), rows.map { it.key })
        assertEquals("Popular providers", providerListSectionTitle(rows, 0))
    }

    fun `test connected providers appear first and are not duplicated in popular section`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(provider("anthropic", "Anthropic"), provider("openai", "OpenAI")),
                connected = listOf("anthropic"),
            ),
            "",
        )

        assertEquals(listOf("anthropic", "openai"), rows.map { it.key })
        assertEquals("Connected providers", providerListSectionTitle(rows, 0))
        assertEquals("Popular providers", providerListSectionTitle(rows, 1))
        assertEquals(listOf(ProviderListAction.DISCONNECT), rows[0].actions)
        assertTrue(rows[0].connected)
    }

    fun `test source custom catalog providers remain visible while configured custom providers are connected`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(
                    provider("anthropic", "Anthropic", source = "custom"),
                    provider("available-custom", "Available Custom", source = "custom"),
                    provider("local-openai", "Local OpenAI", source = "custom"),
                ),
                config = mapOf("local-openai" to CustomProviderConfigDto("local-openai", npm = "@ai-sdk/openai-compatible")),
            ),
            "",
        )

        assertEquals(listOf("local-openai", "anthropic", "available-custom"), rows.map { it.key })
        assertEquals("Connected providers", providerListSectionTitle(rows, 0))
        assertEquals("Popular providers", providerListSectionTitle(rows, 1))
        assertEquals("All providers", providerListSectionTitle(rows, 2))
        assertEquals(listOf(ProviderListAction.DISCONNECT), rows[0].actions)
    }

    fun `test unconfigured openai compatible template provider is hidden`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(provider("openai-compatible", "OpenAI Compatible", source = "custom")),
            ),
            "",
        )

        assertTrue(rows.isEmpty())
    }

    fun `test connected kilo gateway has no provider settings actions`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(provider("kilo", "Kilo Gateway")),
                connected = listOf("kilo"),
            ),
            "",
        )

        assertEquals(listOf("kilo"), rows.map { it.key })
        assertEquals("Connected providers", providerListSectionTitle(rows, 0))
        assertTrue(rows.single().actions.isEmpty())
    }

    fun `test disabled popular provider appears in all providers with enable`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(provider("anthropic", "Anthropic"), provider("openai", "OpenAI")),
                disabled = listOf("anthropic"),
            ),
            "",
        )

        assertEquals(listOf("openai", "anthropic"), rows.map { it.key })
        assertEquals("All providers", providerListSectionTitle(rows, 1))
        assertEquals(listOf(ProviderListAction.ENABLE), rows[1].actions)
    }

    fun `test non popular providers appear in all providers alphabetically`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(
                    provider("zeta", "Zeta"),
                    provider("alpha", "Alpha"),
                    provider("openai", "OpenAI"),
                ),
            ),
            "",
        )

        assertEquals(listOf("openai", "alpha", "zeta"), rows.map { it.key })
        assertEquals("All providers", providerListSectionTitle(rows, 1))
    }

    fun `test filtering by provider name updates rows and sections`() {
        val content = content()
        edt {
            content.update(
                ProviderSettingsDto(
                    providers = listOf(
                        provider("openai", "OpenAI"),
                        provider("anthropic", "Anthropic"),
                        provider("alpha", "Alpha Labs"),
                    ),
                ),
            )

            search(content).text = "open"

            val rows = rows(content)
            assertEquals(listOf("openai"), rows.map { it.key })
            assertEquals("Popular providers", providerListSectionTitle(rows, 0))
        }
    }

    fun `test filtering does not match provider id`() {
        val rows = providerListRows(
            ProviderSettingsDto(
                providers = listOf(provider("openai-compatible", "Local")),
            ),
            "openai",
        )

        assertTrue(rows.isEmpty())
    }

    fun `test renderer hit test maps actions`() {
        edt {
            val row = ProviderListRow(provider("cloudflare", "Cloudflare"), "All providers", listOf(ProviderListAction.OAUTH, ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val bounds = Rectangle(0, 0, 320, 48)
            val areas = ProviderListRenderer.actionBounds(list, bounds, row, selected = true)

            assertEquals(ProviderListAction.CONNECT, ProviderListRenderer.actionAt(list, bounds, center(areas.getValue(ProviderListAction.CONNECT)), row, selected = true))
            assertEquals(ProviderListAction.OAUTH, ProviderListRenderer.actionAt(list, bounds, center(areas.getValue(ProviderListAction.OAUTH)), row, selected = true))
            assertNull(ProviderListRenderer.actionAt(list, bounds, Point(4, 4), row, selected = true))
            assertTrue(ProviderListRenderer.actionBounds(list, bounds, row, selected = false).isEmpty())
        }
    }

    fun `test renderer keeps connected disconnect action visible when unselected`() {
        edt {
            val row = ProviderListRow(provider("openai", "OpenAI"), "Connected providers", listOf(ProviderListAction.DISCONNECT), connected = true)
            val list = JBList(listOf(row))
            val bounds = Rectangle(0, 0, 320, 48)
            val area = ProviderListRenderer.actionBounds(list, bounds, row, selected = false).getValue(ProviderListAction.DISCONNECT)

            assertEquals(ProviderListAction.DISCONNECT, ProviderListRenderer.actionAt(list, bounds, center(area), row, selected = false))
        }
    }

    fun `test renderer ignores disabled env disconnect action`() {
        edt {
            val row = ProviderListRow(provider("env", "Env", source = "env"), "All providers", listOf(ProviderListAction.DISCONNECT))
            val list = JBList(listOf(row))
            val bounds = Rectangle(0, 0, 320, 48)
            val area = ProviderListRenderer.actionBounds(list, bounds, row, selected = true).getValue(ProviderListAction.DISCONNECT)

            assertNull(ProviderListRenderer.actionAt(list, bounds, center(area), row, selected = true))
        }
    }

    fun `test renderer exposes action labels`() {
        edt {
            val row = ProviderListRow(provider("cloudflare", "Cloudflare"), "All providers", listOf(ProviderListAction.OAUTH, ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val renderer = ProviderListRenderer(com.intellij.ui.CollectionListModel(listOf(row)))

            renderer.getListCellRendererComponent(list, row, 0, true, false)

            assertEquals(listOf("OAuth", "Connect"), renderer.actionTexts())
        }
    }

    fun `test renderer hides unselected unconnected action labels`() {
        edt {
            val row = ProviderListRow(provider("cloudflare", "Cloudflare"), "All providers", listOf(ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val renderer = ProviderListRenderer(com.intellij.ui.CollectionListModel(listOf(row)))

            renderer.getListCellRendererComponent(list, row, 0, false, false)

            assertTrue(renderer.actionTexts().isEmpty())
        }
    }

    fun `test renderer uses standard button foreground for actions`() {
        edt {
            val row = ProviderListRow(provider("cloudflare", "Cloudflare"), "All providers", listOf(ProviderListAction.OAUTH, ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val renderer = ProviderListRenderer(com.intellij.ui.CollectionListModel(listOf(row)))

            renderer.getListCellRendererComponent(list, row, 0, true, false)

            val fg = UIManager.getColor("Button.foreground") ?: UIUtil.getLabelForeground()
            val labels = components(renderer).filterIsInstance<JBLabel>().filter { it.text in listOf("OAuth", "Connect") }
            assertEquals(listOf(fg, fg), labels.map { it.foreground })
        }
    }

    fun `test renderer exposes provider icon and vscode note`() {
        edt {
            val row = ProviderListRow(
                provider(
                    "openai",
                    "OpenAI",
                    metadata = ProviderMetadataDto(
                        noteKey = "settings.providers.note.openai",
                        note = "GPT and Codex models with API key or ChatGPT login",
                        icon = "openai",
                    ),
                ),
                "Popular providers",
                listOf(ProviderListAction.CONNECT),
            )
            val list = JBList(listOf(row))
            val renderer = ProviderListRenderer(com.intellij.ui.CollectionListModel(listOf(row)))

            renderer.getListCellRendererComponent(list, row, 0, true, false)

            assertTrue(renderer.providerIconVisible())
            assertEquals(Dimension(JBUI.scale(20), JBUI.scale(20)), renderer.providerIconSize())
            assertEquals("GPT and Codex models with API key or ChatGPT login", renderer.descriptionText())
        }
    }

    fun `test renderer falls back to generic description without metadata`() {
        edt {
            val row = ProviderListRow(provider("openai", "OpenAI"), "Popular providers", listOf(ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val renderer = ProviderListRenderer(com.intellij.ui.CollectionListModel(listOf(row)))

            renderer.getListCellRendererComponent(list, row, 0, true, false)

            assertEquals("catalog · 1 models", renderer.descriptionText())
        }
    }

    fun `test action bounds are vertically centered`() {
        edt {
            val row = ProviderListRow(provider("openai", "OpenAI"), "Popular providers", listOf(ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val bounds = Rectangle(0, 10, 320, 80)
            val area = ProviderListRenderer.actionBounds(list, bounds, row, selected = true).getValue(ProviderListAction.CONNECT)

            assertTrue(kotlin.math.abs((bounds.y + bounds.height / 2) - (area.y + area.height / 2)) <= 1)
        }
    }

    fun `test renderer action labels paint with non button border`() {
        edt {
            val row = ProviderListRow(provider("cloudflare", "Cloudflare"), "All providers", listOf(ProviderListAction.CONNECT))
            val list = JBList(listOf(row))
            val renderer = ProviderListRenderer(com.intellij.ui.CollectionListModel(listOf(row)))

            renderer.getListCellRendererComponent(list, row, 0, false, false)
            renderer.setSize(320, 64)
            renderer.doLayout()

            val image = BufferedImage(320, 64, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                renderer.paint(g)
            } finally {
                g.dispose()
            }
        }
    }

    fun `test provider reload clears loading overlay after state loads`() {
        val rpc = installProvider(providerState(provider("openai", "OpenAI")))
        val panel = edt { createUi() }

        flushUntil { rpc.stateCalls.isNotEmpty() && edt { rows(panel).map { it.key } == listOf("openai") && !text(panel).contains("Loading providers") } }

        edt {
            assertEquals(listOf("openai"), rows(panel).map { it.key })
            assertFalse(text(panel).contains("Loading providers"))
        }
    }

    fun `test provider action failure returns error state`() = runBlocking {
        val cs = CoroutineScope(SupervisorJob())
        scope = cs
        val rpc = FakeProviderRpcApi()
        rpc.state = providerState(provider("openai", "OpenAI"))
        rpc.disconnectError = IllegalStateException("Kilo backend is not ready")
        val service = KiloProviderService(cs, rpc)

        val result = withContext(kotlinx.coroutines.Dispatchers.Default) {
            service.disconnect(ProviderDisconnectDto("/test", "openai"))
        }

        assertEquals("Kilo backend is not ready", result.error)
        assertEquals(listOf("openai"), result.state.providers.map { it.id })
        assertEquals(listOf("/test"), rpc.stateCalls)
    }

    fun `test stale reload result is ignored after newer reload`() {
        val first = CompletableDeferred<ProviderSettingsDto>()
        val second = CompletableDeferred<ProviderSettingsDto>()
        val rpc = installProvider(ProviderSettingsDto())
        rpc.states.add(first)
        rpc.states.add(second)
        val panel = edt { createUi() }

        flushUntil { rpc.stateCalls.size == 1 }
        edt { panel.reload() }
        flushUntil { rpc.stateCalls.size == 2 }
        second.complete(providerState(provider("new", "New")))
        flushUntil { edt { rows(panel).map { it.key } == listOf("new") } }
        first.complete(providerState(provider("old", "Old")))
        flushUntil { first.isCompleted }

        edt { assertEquals(listOf("new"), rows(panel).map { it.key }) }
    }

    fun `test dispose ignores pending reload completion`() {
        val state = CompletableDeferred<ProviderSettingsDto>()
        val rpc = installProvider(ProviderSettingsDto())
        rpc.states.add(state)
        val panel = edt { createUi() }

        flushUntil { rpc.stateCalls.size == 1 }
        edt {
            panel.dispose()
            ui = null
        }
        state.complete(providerState(provider("openai", "OpenAI")))
        flushUntil { state.isCompleted }

        edt { assertTrue(rows(panel).isEmpty()) }
    }

    private fun content() = edt { ProvidersContent({}, {}, {}, {}, {}) }

    private fun createUi(): ProvidersSettingsUi {
        val cs = CoroutineScope(SupervisorJob())
        scope = cs
        val panel = ProvidersSettingsUi(cs, "/test")
        ui = panel
        return panel
    }

    private fun installProvider(state: ProviderSettingsDto): FakeProviderRpcApi {
        val cs = CoroutineScope(SupervisorJob())
        scope = cs
        val rpc = FakeProviderRpcApi()
        rpc.state = state
        ApplicationManager.getApplication().replaceService(
            KiloProviderService::class.java,
            KiloProviderService(cs, rpc),
            testRootDisposable,
        )
        return rpc
    }

    private fun providerState(vararg providers: ProviderSettingsProviderDto) = ProviderSettingsDto(providers = providers.toList())

    private fun provider(
        id: String,
        name: String,
        source: String? = null,
        metadata: ProviderMetadataDto? = null,
    ) = ProviderSettingsProviderDto(
        id = id,
        name = name,
        source = source,
        metadata = metadata,
        models = mapOf("model" to ModelDto("model", "Model")),
    )

    private fun rows(component: JComponent): List<ProviderListRow> {
        val model = list(component).model
        return (0 until model.size).map { model.getElementAt(it) }
    }

    private fun list(component: JComponent) = components(component).filterIsInstance<JBList<ProviderListRow>>().single()

    private fun search(component: JComponent) = components(component).filterIsInstance<SearchTextField>().single()

    private fun center(rect: Rectangle) = Point(rect.x + rect.width / 2, rect.y + rect.height / 2)

    private fun components(component: java.awt.Component): List<java.awt.Component> {
        val out = mutableListOf<java.awt.Component>()
        fun visit(c: java.awt.Component) {
            out += c
            if (c is Container) c.components.forEach { visit(it) }
        }
        visit(component)
        return out
    }

    private fun text(root: Container): String {
        val out = mutableListOf<String>()
        for (comp in components(root)) {
            if (!comp.isVisible) continue
            when (comp) {
                is JButton -> comp.text?.let { out.add(it) }
                is JBLabel -> comp.text?.let { out.add(it) }
            }
        }
        return out.joinToString("\n")
    }

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun flushUntil(done: () -> Boolean) = runBlocking {
        repeat(20) {
            delay(100)
            edt { UIUtil.dispatchAllInvocationEvents() }
            if (done()) return@runBlocking
        }
        edt { UIUtil.dispatchAllInvocationEvents() }
        assertTrue(done())
    }
}
