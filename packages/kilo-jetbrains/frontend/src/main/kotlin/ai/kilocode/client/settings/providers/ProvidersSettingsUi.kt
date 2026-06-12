package ai.kilocode.client.settings.providers

import ai.kilocode.client.app.KiloProviderService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.SettingsOverlayPanel
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.dto.CustomModelDto
import ai.kilocode.rpc.dto.CustomProviderSaveDto
import ai.kilocode.rpc.dto.ProviderAuthMethodDto
import ai.kilocode.rpc.dto.ProviderAuthOptionDto
import ai.kilocode.rpc.dto.ProviderConnectDto
import ai.kilocode.rpc.dto.ProviderDisconnectDto
import ai.kilocode.rpc.dto.ProviderEnableDto
import ai.kilocode.rpc.dto.ProviderOAuthAuthorizeDto
import ai.kilocode.rpc.dto.ProviderOAuthCallbackDto
import ai.kilocode.rpc.dto.ProviderSettingsDto
import ai.kilocode.rpc.dto.ProviderSettingsProviderDto
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.DefaultListCellRenderer
import javax.swing.JPanel
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent

private val edt = Dispatchers.EDT + ModalityState.any().asContextElement()

internal class ProvidersSettingsUi(
    private val cs: CoroutineScope,
    private val directory: String,
) : SettingsOverlayPanel(), Disposable {
    companion object {
        val LOG = KiloLog.create(ProvidersSettingsUi::class.java)
    }

    private val view = ProvidersContent(::connect, ::oauth, ::disconnect, ::enable, ::reload)
    private var state = ProviderSettingsDto()
    private var job: Job? = null
    private var request = 0
    private var disposed = false

    init {
        content.add(view, BorderLayout.CENTER)
        reload()
    }

    @RequiresEdt
    fun reload() {
        checkEdt()
        LOG.info("provider settings ui reload: start dir=$directory")
        syncLoading()
        launch("reload") { id ->
            val next = service<KiloProviderService>().state(directory)
            LOG.info("provider settings ui reload: state providers=${next.providers.size} errors=${next.errors.size}")
            apply(id, next, null)
        }
    }

    @RequiresEdt
    private fun syncLoading() {
        checkEdt()
        showProgress(KiloBundle.message("settings.providers.loading"))
    }

    @RequiresEdt
    private fun connect(provider: ProviderSettingsProviderDto) {
        checkEdt()
        val methods = state.auth[provider.id].orEmpty().filter { it.type == "api" }
        val dialog = ApiKeyDialog(provider.name, methods.firstOrNull())
        if (!dialog.showAndGet()) return
        val key = dialog.key()
        val metadata = dialog.metadata()
        syncLoading()
        launch("connect provider=${provider.id}") { id ->
            val result = service<KiloProviderService>().connect(ProviderConnectDto(directory, provider.id, key, metadata))
            apply(id, result.state, result.error)
        }
    }

    @RequiresEdt
    private fun oauth(provider: ProviderSettingsProviderDto) {
        checkEdt()
        val methods = state.auth[provider.id].orEmpty().filter { it.type == "oauth" }
        val method = state.auth[provider.id].orEmpty().indexOf(methods.firstOrNull()).coerceAtLeast(0).toString()
        syncLoading()
        launch("authorize provider=${provider.id}") { id ->
            val ready = service<KiloProviderService>().authorize(ProviderOAuthAuthorizeDto(directory, provider.id, method))
            val code = withContext(edt) {
                if (!active(id)) return@withContext null
                ready.url?.let(BrowserUtil::browse)
                if (ready.method == "code") Messages.showInputDialog(this@ProvidersSettingsUi, ready.instructions ?: "Enter OAuth code", provider.name, null) else null
            }
            val current = withContext(edt) { active(id) }
            if (!current) return@launch
            val result = service<KiloProviderService>().callback(ProviderOAuthCallbackDto(directory, provider.id, method, code))
            apply(id, result.state, result.error)
        }
    }

    @RequiresEdt
    private fun disconnect(provider: ProviderSettingsProviderDto) {
        checkEdt()
        syncLoading()
        launch("disconnect provider=${provider.id}") { id ->
            val result = service<KiloProviderService>().disconnect(ProviderDisconnectDto(directory, provider.id))
            apply(id, result.state, result.error)
        }
    }

    @RequiresEdt
    private fun enable(provider: ProviderSettingsProviderDto) {
        checkEdt()
        syncLoading()
        launch("enable provider=${provider.id}") { id ->
            val result = service<KiloProviderService>().enable(ProviderEnableDto(directory, provider.id))
            apply(id, result.state, result.error)
        }
    }

    @RequiresEdt
    private fun custom() {
        checkEdt()
        val dialog = CustomProviderDialog()
        if (!dialog.showAndGet()) return
        val input = dialog.input(directory)
        syncLoading()
        launch("save custom provider") { id ->
            val result = service<KiloProviderService>().saveCustom(input)
            apply(id, result.state, result.error)
        }
    }

    @RequiresEdt
    private fun launch(name: String, block: suspend (Int) -> Unit) {
        checkEdt()
        val id = ++request
        job?.cancel()
        job = cs.launch {
            val start = System.currentTimeMillis()
            LOG.info("provider settings ui $name: coroutine start dir=$directory")
            try {
                block(id)
                LOG.info("provider settings ui $name: coroutine completed durationMs=${System.currentTimeMillis() - start}")
            } catch (e: TimeoutCancellationException) {
                LOG.warn("provider settings ui $name: coroutine timed out durationMs=${System.currentTimeMillis() - start}", e)
                withContext(edt) {
                    if (!active(id)) return@withContext
                    showError("${e::class.simpleName}: ${e.message}")
                }
            } catch (e: CancellationException) {
                LOG.info("provider settings ui $name: coroutine cancelled durationMs=${System.currentTimeMillis() - start}")
                throw e
            } catch (e: Exception) {
                LOG.warn("provider settings ui $name: coroutine failed durationMs=${System.currentTimeMillis() - start}", e)
                withContext(edt) {
                    if (!active(id)) return@withContext
                    showError("${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    private suspend fun apply(id: Int, next: ProviderSettingsDto, error: String?) {
        withContext(edt) {
            if (!active(id)) return@withContext
            LOG.info("provider settings ui apply: start providers=${next.providers.size} errors=${next.errors.size} message=${error != null}")
            state = next
            view.update(next)
            val text = error ?: next.errors.joinToString("; ") { it.detail ?: it.resource }.takeIf { it.isNotBlank() }
            if (text != null) showError(text) else clearProgress()
            LOG.info("provider settings ui apply: completed providers=${next.providers.size}")
        }
    }

    @RequiresEdt
    override fun dispose() {
        checkEdt()
        disposed = true
        request++
        job?.cancel()
        job = null
    }

    @RequiresEdt
    private fun active(id: Int): Boolean {
        checkEdt()
        return !disposed && id == request
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) { "Provider settings UI updates must run on EDT" }
    }
}

internal class ProvidersContent(
    private val connect: (ProviderSettingsProviderDto) -> Unit,
    private val oauth: (ProviderSettingsProviderDto) -> Unit,
    private val disconnect: (ProviderSettingsProviderDto) -> Unit,
    private val enable: (ProviderSettingsProviderDto) -> Unit,
    private val reload: () -> Unit,
) : JPanel(BorderLayout()) {
    private val top = Stack.horizontal(UiStyle.Gap.sm())
    private val model = CollectionListModel<ProviderListRow>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.text = KiloBundle.message("settings.providers.noMatches")
    }
    private val search = SearchTextField(false).apply {
        textEditor.emptyText.text = KiloBundle.message("settings.providers.search")
    }
    private var state = ProviderSettingsDto()

    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(UiStyle.Gap.pad(), UiStyle.Gap.pad(), UiStyle.Gap.pad(), UiStyle.Gap.pad())
        top.next(JButton(KiloBundle.message("settings.providers.refresh")).apply { addActionListener { reload() } })
        add(top, BorderLayout.NORTH)
        list.cellRenderer = ProviderListRenderer(model)
        list.registerKeyboardAction(
            { primary() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        search.textEditor.registerKeyboardAction(
            { primary() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        search.textEditor.registerKeyboardAction(
            { move(-1) },
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
            JComponent.WHEN_FOCUSED,
        )
        search.textEditor.registerKeyboardAction(
            { move(1) },
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
            JComponent.WHEN_FOCUSED,
        )
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                sync()
            }
        })
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true)) return
                val idx = list.locationToIndex(e.point)
                val bounds = idx.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) } ?: return
                if (!bounds.contains(e.point)) return
                val row = model.getElementAt(idx)
                val action = ProviderListRenderer.actionAt(list, bounds, e.point, row, idx == list.selectedIndex) ?: return
                activate(row, action)
                e.consume()
            }
        })
        ScrollingUtil.installActions(list)
        val body = JPanel(BorderLayout(0, UiStyle.Gap.sm()))
        body.add(search, BorderLayout.NORTH)
        body.add(ScrollPaneFactory.createScrollPane(list).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
    }

    @RequiresEdt
    fun update(state: ProviderSettingsDto) {
        checkEdt()
        ProvidersSettingsUi.LOG.info("provider settings content update: start providers=${state.providers.size} connected=${state.connected.size} disabled=${state.disabled.size}")
        this.state = state
        sync()
        ProvidersSettingsUi.LOG.info("provider settings content update: completed rows=${model.size}")
    }

    @RequiresEdt
    private fun sync(prefer: String? = list.selectedValue?.key, at: Int? = null) {
        checkEdt()
        val rows = providerListRows(state, search.text)
        model.replaceAll(rows)
        val idx = at?.let { providerListIndex(rows, it) }?.takeIf { it >= 0 }
            ?: providerListIndex(rows, prefer).takeIf { it >= 0 }
            ?: rows.indices.firstOrNull()
            ?: -1
        if (idx >= 0) choose(idx)
        else list.clearSelection()
    }

    @RequiresEdt
    private fun choose(idx: Int) {
        checkEdt()
        list.selectedIndex = idx
        ScrollingUtil.ensureIndexIsVisible(list, idx, 0)
    }

    @RequiresEdt
    private fun move(step: Int) {
        checkEdt()
        val size = model.size
        if (size <= 0) return
        val idx = ((list.selectedIndex.takeIf { it >= 0 } ?: 0) + step).coerceIn(0, size - 1)
        choose(idx)
    }

    @RequiresEdt
    private fun primary() {
        checkEdt()
        val row = list.selectedValue ?: return
        val action = ProviderListRenderer.visibleActions(row, true).firstOrNull() ?: return
        activate(row, action)
    }

    @RequiresEdt
    private fun activate(row: ProviderListRow, action: ProviderListAction) {
        checkEdt()
        if (!row.enabled(action)) return
        when (action) {
            ProviderListAction.CONNECT -> connect(row.provider)
            ProviderListAction.OAUTH -> oauth(row.provider)
            ProviderListAction.DISCONNECT -> disconnect(row.provider)
            ProviderListAction.ENABLE -> enable(row.provider)
        }
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) { "Provider settings content updates must run on EDT" }
    }
}

private class ApiKeyDialog(title: String, method: ProviderAuthMethodDto?) : DialogWrapper(true) {
    private val key = JBPasswordField()
    private val fields = method?.prompts.orEmpty().associateWith { prompt ->
        if (prompt.options.isNotEmpty()) optionBox(prompt.options) as JComponent else JBTextField()
    }

    init {
        this.title = title
        init()
        initValidation()
    }

    @RequiresEdt
    fun key(): String = String(key.password)

    @RequiresEdt
    fun metadata(): Map<String, String> = fields.mapValues { (_, field) ->
        when (field) {
            is JComboBox<*> -> (field.selectedItem as? ProviderAuthOptionDto)?.value ?: field.selectedItem?.toString().orEmpty()
            is JBTextField -> field.text
            else -> ""
        }
    }.mapKeys { it.key.key }.filterValues { it.isNotBlank() }

    override fun createCenterPanel(): JComponent {
        val panel = Stack.vertical(UiStyle.Gap.sm())
        panel.next(JBLabel(KiloBundle.message("settings.providers.apiKey")))
        panel.next(key)
        fields.forEach { (prompt, field) ->
            panel.next(JBLabel(prompt.label))
            panel.next(field)
        }
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (key().isBlank()) return ValidationInfo(KiloBundle.message("settings.providers.apiKeyRequired"), key)
        return null
    }

    private fun optionBox(options: List<ProviderAuthOptionDto>): JComboBox<ProviderAuthOptionDto> {
        val box = JComboBox(options.toTypedArray())
        box.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                val item = value as? ProviderAuthOptionDto
                return super.getListCellRendererComponent(list, item?.label.orEmpty(), index, selected, focus)
            }
        }
        return box
    }
}

private class CustomProviderDialog : DialogWrapper(true) {
    private val id = JBTextField()
    private val name = JBTextField()
    private val url = JBTextField()
    private val key = JBPasswordField()
    private val env = JBTextField()
    private val models = JBTextField()

    init {
        title = KiloBundle.message("settings.providers.customTitle")
        init()
        initValidation()
    }

    @RequiresEdt
    fun input(directory: String) = CustomProviderSaveDto(
        directory = directory,
        id = id.text.trim(),
        name = name.text.trim(),
        baseUrl = url.text.trim(),
        apiKey = String(key.password).takeIf { it.isNotBlank() },
        envVar = env.text.trim().takeIf { it.isNotBlank() },
        models = models.text.split(',').mapNotNull { raw ->
            raw.trim().takeIf { it.isNotBlank() }?.let { CustomModelDto(it, it) }
        },
    )

    override fun createCenterPanel(): JComponent {
        val panel = Stack.vertical(UiStyle.Gap.sm())
        listOf(
            KiloBundle.message("settings.providers.customId") to id,
            KiloBundle.message("settings.providers.customName") to name,
            KiloBundle.message("settings.providers.customUrl") to url,
            KiloBundle.message("settings.providers.apiKey") to key,
            KiloBundle.message("settings.providers.customEnv") to env,
            KiloBundle.message("settings.providers.customModels") to models,
        ).forEach { (label, field) ->
            panel.next(JBLabel(label))
            panel.next(field)
        }
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (id.text.isBlank()) return ValidationInfo(KiloBundle.message("settings.providers.customIdRequired"), id)
        if (url.text.isBlank()) return ValidationInfo(KiloBundle.message("settings.providers.customUrlRequired"), url)
        return null
    }
}
