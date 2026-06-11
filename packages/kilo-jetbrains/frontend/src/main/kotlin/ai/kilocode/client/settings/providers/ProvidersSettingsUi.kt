package ai.kilocode.client.settings.providers

import ai.kilocode.client.app.KiloProviderService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.BaseContentPanel
import ai.kilocode.client.settings.base.SettingsRow
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
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.DefaultListCellRenderer
import javax.swing.JPanel
import javax.swing.JList

class ProvidersSettingsUi(
    private val cs: CoroutineScope,
    private val directory: String,
) : JPanel(BorderLayout()), Disposable {
    companion object {
        val LOG = KiloLog.create(ProvidersSettingsUi::class.java)
    }

    private val content = ProvidersContent(::connect, ::oauth, ::disconnect, ::enable, ::custom, ::reload)
    private val scroll = JBScrollPane(content)
    private var state = ProviderSettingsDto()

    init {
        add(scroll, BorderLayout.CENTER)
        reload()
    }

    fun reload() {
        LOG.info("provider settings ui reload: start dir=$directory")
        syncLoading()
        launch("reload") {
            val next = service<KiloProviderService>().state(directory)
            LOG.info("provider settings ui reload: state providers=${next.providers.size} errors=${next.errors.size}")
            apply(next, null)
        }
    }

    private fun syncLoading() {
        content.loading()
    }

    private fun connect(provider: ProviderSettingsProviderDto) {
        val methods = state.auth[provider.id].orEmpty().filter { it.type == "api" }
        val dialog = ApiKeyDialog(provider.name, methods.firstOrNull())
        if (!dialog.showAndGet()) return
        content.loading()
        launch("connect provider=${provider.id}") {
            val result = service<KiloProviderService>().connect(ProviderConnectDto(directory, provider.id, dialog.key(), dialog.metadata()))
            apply(result.state, result.error)
        }
    }

    private fun oauth(provider: ProviderSettingsProviderDto) {
        val methods = state.auth[provider.id].orEmpty().filter { it.type == "oauth" }
        val method = state.auth[provider.id].orEmpty().indexOf(methods.firstOrNull()).coerceAtLeast(0).toString()
        content.loading()
        launch("authorize provider=${provider.id}") {
            val ready = service<KiloProviderService>().authorize(ProviderOAuthAuthorizeDto(directory, provider.id, method))
            val code = withContext(Dispatchers.Main) {
                ready.url?.let(BrowserUtil::browse)
                if (ready.method == "code") Messages.showInputDialog(this@ProvidersSettingsUi, ready.instructions ?: "Enter OAuth code", provider.name, null) else null
            }
            val result = service<KiloProviderService>().callback(ProviderOAuthCallbackDto(directory, provider.id, method, code))
            apply(result.state, result.error)
        }
    }

    private fun disconnect(provider: ProviderSettingsProviderDto) {
        content.loading()
        launch("disconnect provider=${provider.id}") {
            val result = service<KiloProviderService>().disconnect(ProviderDisconnectDto(directory, provider.id))
            apply(result.state, result.error)
        }
    }

    private fun enable(provider: ProviderSettingsProviderDto) {
        content.loading()
        launch("enable provider=${provider.id}") {
            val result = service<KiloProviderService>().enable(ProviderEnableDto(directory, provider.id))
            apply(result.state, result.error)
        }
    }

    private fun custom() {
        val dialog = CustomProviderDialog()
        if (!dialog.showAndGet()) return
        content.loading()
        launch("save custom provider") {
            val result = service<KiloProviderService>().saveCustom(dialog.input(directory))
            apply(result.state, result.error)
        }
    }

    private fun launch(name: String, block: suspend () -> Unit) {
        cs.launch {
            val start = System.currentTimeMillis()
            LOG.info("provider settings ui $name: coroutine start dir=$directory")
            try {
                block()
                LOG.info("provider settings ui $name: coroutine completed durationMs=${System.currentTimeMillis() - start}")
            } catch (e: Exception) {
                LOG.warn("provider settings ui $name: coroutine failed durationMs=${System.currentTimeMillis() - start}", e)
                withContext(Dispatchers.Main) {
                    content.error("${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    private suspend fun apply(next: ProviderSettingsDto, error: String?) {
        withContext(Dispatchers.Main) {
            LOG.info("provider settings ui apply: start providers=${next.providers.size} errors=${next.errors.size} message=${error != null}")
            state = next
            content.update(next, error)
            LOG.info("provider settings ui apply: completed providers=${next.providers.size}")
        }
    }

    override fun dispose() = Unit
}

internal class ProvidersContent(
    private val connect: (ProviderSettingsProviderDto) -> Unit,
    private val oauth: (ProviderSettingsProviderDto) -> Unit,
    private val disconnect: (ProviderSettingsProviderDto) -> Unit,
    private val enable: (ProviderSettingsProviderDto) -> Unit,
    private val custom: () -> Unit,
    private val reload: () -> Unit,
) : BaseContentPanel() {
    private val top = Stack.horizontal(UiStyle.Gap.sm())
    private val status = JBLabel("").apply { foreground = UIUtil.getContextHelpForeground() }
    private val connected: ai.kilocode.client.settings.base.SettingsRows
    private val available: ai.kilocode.client.settings.base.SettingsRows
    private val disabled: ai.kilocode.client.settings.base.SettingsRows

    init {
        border = JBUI.Borders.empty(UiStyle.Gap.pad(), UiStyle.Gap.pad(), UiStyle.Gap.pad(), UiStyle.Gap.pad())
        top.next(JButton(KiloBundle.message("settings.providers.addCustom")).apply { addActionListener { custom() } })
        top.next(JButton(KiloBundle.message("settings.providers.refresh")).apply { addActionListener { reload() } })
        next(top)
        next(status)
        connected = section(KiloBundle.message("settings.providers.connected"))
        available = section(KiloBundle.message("settings.providers.available"))
        disabled = section(KiloBundle.message("settings.providers.disabled"))
    }

    fun loading() {
        status.text = KiloBundle.message("settings.providers.loading")
    }

    fun error(message: String) {
        status.text = message
    }

    fun update(state: ProviderSettingsDto, error: String? = null) {
        ProvidersSettingsUi.LOG.info("provider settings content update: start providers=${state.providers.size} connected=${state.connected.size} disabled=${state.disabled.size}")
        status.text = error ?: state.errors.joinToString("; ") { it.detail ?: it.resource }
        val ids = state.connected.toSet()
        val disabledIds = state.disabled.toSet()
        val connectedKeys = mutableSetOf<String>()
        val availableKeys = mutableSetOf<String>()
        val disabledKeys = mutableSetOf<String>()
        state.providers.sortedWith(compareBy<ProviderSettingsProviderDto> { it.name.lowercase() }.thenBy { it.id }).forEach { provider ->
            val target = when {
                provider.id in disabledIds -> disabled
                configured(provider, state, ids) -> connected
                else -> available
            }
            val keys = when (target) {
                connected -> connectedKeys
                available -> availableKeys
                else -> disabledKeys
            }
            val key = provider.id
            keys.add(key)
            val row = SettingsRow(provider.name, description(provider), buttons(provider, state, disabledIds))
            target.row(key, row)
        }
        connected.retain(connectedKeys)
        available.retain(availableKeys)
        disabled.retain(disabledKeys)
        ProvidersSettingsUi.LOG.info("provider settings content update: completed connected=${connectedKeys.size} available=${availableKeys.size} disabled=${disabledKeys.size}")
    }

    private fun description(provider: ProviderSettingsProviderDto): String {
        val source = provider.source ?: "catalog"
        val models = provider.models.size
        return "$source · $models models"
    }

    private fun buttons(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto, disabled: Set<String>): JComponent {
        val row = Stack.horizontal(UiStyle.Gap.sm())
        if (provider.id in disabled) {
            row.next(JButton(KiloBundle.message("settings.providers.enable")).apply { addActionListener { enable(provider) } })
            return row
        }
        if (configured(provider, state, state.connected.toSet())) {
            row.next(JButton(KiloBundle.message("settings.providers.disconnect")).apply { isEnabled = provider.source != "env"; addActionListener { disconnect(provider) } })
            return row
        }
        val methods = methods(provider, state)
        if (methods.any { it.type == "api" }) row.next(JButton(KiloBundle.message("settings.providers.connect")).apply { addActionListener { connect(provider) } })
        if (methods.any { it.type == "oauth" }) row.next(JButton(KiloBundle.message("settings.providers.oauth")).apply { addActionListener { oauth(provider) } })
        return row
    }

    private fun methods(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto): List<ProviderAuthMethodDto> {
        val methods = state.auth[provider.id]
        if (!methods.isNullOrEmpty()) return methods
        return listOf(ProviderAuthMethodDto("api", "API key"))
    }

    private fun configured(provider: ProviderSettingsProviderDto, state: ProviderSettingsDto, ids: Set<String>) =
        provider.id in ids || provider.key != null || provider.source == "config" || provider.id in state.config
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

    fun key(): String = String(key.password)

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
