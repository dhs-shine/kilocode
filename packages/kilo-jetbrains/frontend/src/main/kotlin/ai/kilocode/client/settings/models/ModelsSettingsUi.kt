package ai.kilocode.client.settings.models

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.ReasoningPicker
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.settings.profile.UserProfileConfigurable
import ai.kilocode.client.settings.profile.edt
import ai.kilocode.client.settings.ui.SettingsRow
import ai.kilocode.client.settings.ui.SettingsRows
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.HAlign
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.layout.VAlign
import ai.kilocode.client.ui.layout.align
import ai.kilocode.rpc.dto.AgentDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.LoadErrorDto
import ai.kilocode.rpc.dto.ModelsWorkspaceDto
import ai.kilocode.rpc.dto.ProvidersDto
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JPanel

internal class ModelsSettingsUi(
    private val cs: CoroutineScope,
    private val app: KiloAppService = service(),
    private val workspaces: KiloWorkspaceService = service(),
    private val directory: String? = null,
) : JPanel(BorderLayout()) {

    private val status = JBLabel(KiloBundle.message("settings.models.loading"))
    private val banner = InlineBanner(
        KiloBundle.message("settings.models.login.message"),
        EditorNotificationPanel.Status.Warning,
    ).showCloseButton(false)
    private val content = Stack.vertical()
    private val rows = SettingsRows()
    private val modes = SettingsRows()
    private val defaults = ModelSettingPicker { update { copy(model = null) } }
    private val small = ModelSettingPicker { update { copy(small = null) } }
    private val subagent = ModelSettingPicker { update { copy(subagent = null, variant = null) } }
    private val variant = ReasoningPicker()
    private val pickers = mutableMapOf<String, ModelSettingPicker>()
    private val jobs = mutableListOf<Job>()

    private var baseline = ModelsDraft()
    private var draft = ModelsDraft()
    private var providers: ProvidersDto? = null
    private var agents: List<AgentDto> = emptyList()
    private var appState: KiloAppStateDto = app.state.value
    private var dir: String? = null
    private var loading = false
    private var loaded = false
    private var errors: List<LoadErrorDto> = emptyList()
    private var allItems: List<ModelPicker.Item> = emptyList()
    private var saving = false

    init {
        status.foreground = UIUtil.getContextHelpForeground()
        defaults.picker.onSelect = { update { copy(model = it.key) } }
        small.picker.onSelect = { update { copy(small = it.key) } }
        subagent.picker.onSelect = { item -> selectSubagent(item) }
        variant.onSelect = { item -> update { copy(variant = item.id) } }
        listOf(defaults, small, subagent).forEach { picker ->
            picker.picker.favorites = { app.favorites.value }
            picker.picker.onFavoriteToggle = { app.toggleModelFavorite(it.provider, it.id) }
        }

        banner.addAction(KiloBundle.message("settings.models.login.action"), Runnable { openProfile(banner) })
        content.next(banner)
        content.gap(UiStyle.Gap.md())
        content.next(status)
        content.gap(UiStyle.Gap.lg())
        content.next(TitledSeparator(KiloBundle.message("settings.models.displayName")))
        rows.row(SettingsRow(
            KiloBundle.message("settings.models.defaultModel.title"),
            KiloBundle.message("settings.models.defaultModel.description"),
            defaults,
        ))
        rows.row(SettingsRow(
            KiloBundle.message("settings.models.smallModel.title"),
            KiloBundle.message("settings.models.smallModel.description"),
            small,
        ))
        rows.row(SettingsRow(
            KiloBundle.message("settings.models.subagentModel.title"),
            KiloBundle.message("settings.models.subagentModel.description"),
            subagent,
        ))
        rows.row(SettingsRow(
            KiloBundle.message("settings.models.subagentVariant.title"),
            KiloBundle.message("settings.models.subagentVariant.description"),
            variant.align(HAlign.RIGHT, VAlign.CENTER),
        ))
        content.next(rows)
        content.next(TitledSeparator(KiloBundle.message("settings.models.modeModels.title")))
        content.next(JBLabel(KiloBundle.message("settings.models.modeModels.description")).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = UiStyle.Fonts.small()
        })
        content.next(modes)
        add(JBScrollPane(content).apply { border = null }, BorderLayout.CENTER)
        sync()
        start()
    }

    @RequiresEdt
    fun modified(): Boolean {
        checkEdt()
        return draft != baseline
    }

    @RequiresEdt
    fun resetDraft() {
        checkEdt()
        draft = baseline
        if (!saving) status.text = ""
        sync()
    }

    @RequiresEdt
    fun applyDraft() {
        checkEdt()
        val prev = baseline
        val next = draft
        val patch = patch(prev, next)
        if (patch.values.isEmpty() && patch.agents.isEmpty()) return
        baseline = next
        saving = true
        status.text = KiloBundle.message("settings.models.save.pending")
        status.foreground = UIUtil.getContextHelpForeground()
        sync()
        cs.launch {
            val ok = app.updateConfig(patch)
            withContext(edt) {
                if (ok) {
                    saving = false
                    status.text = ""
                    sync()
                    return@withContext
                }
                baseline = prev
                saving = false
                status.text = KiloBundle.message("settings.models.save.failed")
                status.foreground = UiStyle.Colors.errorLabelForeground()
                sync()
            }
        }
    }

    @RequiresEdt
    fun updateApp(state: KiloAppStateDto) {
        checkEdt()
        appState = state
        if (state.status != KiloAppStatusDto.READY) {
            loading = false
            loaded = false
            providers = null
            agents = emptyList()
            errors = emptyList()
        }
        val base = modelsDraft(state.config, agents)
        baseline = base
        if (!saving) draft = base
        sync()
        loadModels()
    }

    @RequiresEdt
    fun updateModelsWorkspace(state: ModelsWorkspaceDto) {
        checkEdt()
        providers = state.providers
        agents = state.agents?.agents ?: emptyList()
        errors = state.errors
        loaded = true
        loading = false
        val base = modelsDraft(appState.config, agents)
        baseline = base
        if (!saving) draft = base
        sync()
    }

    @RequiresEdt
    fun updateModels(state: ai.kilocode.rpc.dto.ModelStateDto) {
        checkEdt()
        sync()
    }

    @RequiresEdt
    fun dispose() {
        checkEdt()
        jobs.forEach { it.cancel() }
        jobs.clear()
        cs.cancel()
    }

    private fun start() {
        jobs += cs.launch {
            app.state.collect { state -> withContext(edt) { updateApp(state) } }
        }
        jobs += cs.launch {
            app.models.collect { state -> withContext(edt) { updateModels(state) } }
        }
        jobs += cs.launch { app.connect() }
        val hint = directory ?: return
        jobs += cs.launch {
            val resolved = workspaces.resolveProjectDirectory(hint)
            withContext(edt) {
                dir = resolved
                loaded = false
                loadModels()
                sync()
            }
        }
    }

    @RequiresEdt
    private fun loadModels() {
        checkEdt()
        val root = dir ?: return
        if (appState.status != KiloAppStatusDto.READY || loading || loaded) return
        loading = true
        errors = emptyList()
        sync()
        jobs += cs.launch {
            val state = workspaces.models(root)
            withContext(edt) { updateModelsWorkspace(state) }
        }
    }

    @RequiresEdt
    private fun sync() {
        checkEdt()
        allItems = items(false)
        val smallItems = items(true)
        val hasDir = dir != null || directory != null
        val state = modelsStatus(
            ready = appState.status == KiloAppStatusDto.READY && hasDir,
            loading = loading || (appState.status == KiloAppStatusDto.READY && !loaded && hasDir),
            providers = providers,
            items = allItems.size,
            errors = errors,
            saving = saving,
        )
        val ready = state == ModelsStatus.READY || state == ModelsStatus.MODES_FAILED
        banner.isVisible = modelsLoginBannerVisible(
            ready = appState.status == KiloAppStatusDto.READY,
            authenticated = appState.profile != null,
        )
        if (!saving) {
            when (state) {
                ModelsStatus.UNAVAILABLE -> {
                    status.text = KiloBundle.message("settings.models.unavailable")
                    status.foreground = UIUtil.getContextHelpForeground()
                }
                ModelsStatus.LOADING -> {
                    status.text = KiloBundle.message("settings.models.loading")
                    status.foreground = UIUtil.getContextHelpForeground()
                }
                ModelsStatus.LOAD_FAILED -> {
                    status.text = KiloBundle.message("settings.models.load.failed")
                    status.foreground = UiStyle.Colors.errorLabelForeground()
                }
                ModelsStatus.NO_PROVIDERS -> {
                    status.text = KiloBundle.message("settings.models.noProviders")
                    status.foreground = UIUtil.getContextHelpForeground()
                }
                ModelsStatus.MODES_FAILED -> {
                    status.text = KiloBundle.message("settings.models.modes.failed")
                    status.foreground = UiStyle.Colors.warningLabelForeground()
                }
                ModelsStatus.READY,
                ModelsStatus.SAVING -> {
                    status.text = ""
                    status.foreground = UIUtil.getContextHelpForeground()
                }
            }
        }
        status.isVisible = status.text.isNotBlank()
        defaults.setItems(allItems, draft.model)
        small.setItems(smallItems, draft.small)
        subagent.setItems(allItems, draft.subagent)
        defaults.setClearVisible(draft.model != null)
        small.setClearVisible(draft.small != null)
        subagent.setClearVisible(draft.subagent != null)
        listOf(defaults, small, subagent).forEach { it.isEnabled = ready }
        syncVariant(ready)
        syncModes(ready)
        revalidate()
        repaint()
    }

    private fun items(includeSmall: Boolean): List<ModelPicker.Item> {
        val cfg = providers ?: return emptyList()
        return cfg.providers
            .filter { it.id == KILO_PROVIDER || it.id in cfg.connected }
            .flatMap { provider ->
                provider.models.mapNotNull { (id, model) ->
                    val item = ModelPicker.Item(id, model.name, provider.id, provider.name, model.recommendedIndex, model.free, model.variants)
                    if (!includeSmall && ModelText.small(item)) return@mapNotNull null
                    item
                }
            }
    }

    @RequiresEdt
    private fun syncVariant(ready: Boolean) {
        val item = allItems.firstOrNull { it.key == draft.subagent || it.id == draft.subagent }
        val valid = item?.variants.orEmpty()
        if (draft.variant != null && draft.variant !in valid) draft = draft.copy(variant = valid.firstOrNull())
        if (draft.subagent != null && valid.isEmpty() && draft.variant != null) draft = draft.copy(variant = null)
        variant.setItems(valid.map { ReasoningPicker.Item(it, variantTitle(it)) }, draft.variant)
        variant.isEnabled = ready && valid.isNotEmpty()
        variant.isVisible = valid.isNotEmpty()
    }

    @RequiresEdt
    private fun syncModes(ready: Boolean) {
        val names = agents.map { it.name }.toSet()
        if (names != pickers.keys) {
            modes.removeAll()
            pickers.clear()
            agents.forEach { agent ->
                val picker = ModelSettingPicker { update { copy(agents = this.agents + (agent.name to null)) } }
                picker.picker.favorites = { app.favorites.value }
                picker.picker.onFavoriteToggle = { app.toggleModelFavorite(it.provider, it.id) }
                picker.picker.onSelect = { item -> update { copy(agents = this.agents + (agent.name to item.key)) } }
                pickers[agent.name] = picker
                modes.row(SettingsRow(
                    agent.displayName ?: title(agent.name),
                    agent.description,
                    picker,
                ))
            }
        }
        for ((name, picker) in pickers) {
            val value = draft.agents[name]
            picker.setItems(allItems, value)
            picker.setClearVisible(value != null)
            picker.isEnabled = ready
        }
    }

    private fun selectSubagent(item: ModelPicker.Item) {
        val variant = if (draft.subagent == item.key && draft.variant in item.variants) draft.variant else item.variants.firstOrNull()
        update { copy(subagent = item.key, variant = variant) }
    }

    private fun openProfile(src: JComponent) {
        val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(src))
        if (settings != null) {
            val cfg = settings.find(UserProfileConfigurable.ID)
            if (cfg != null) {
                settings.select(cfg)
                return
            }
        }

        val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault }
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            Predicate { cfg: Configurable ->
                cfg is ConfigurableWithId && cfg.getId() == UserProfileConfigurable.ID
            },
            { cfg: Configurable -> cfg.focusOn(UserProfileConfigurable.FOCUS_ACCOUNT_COMBO) },
        )
    }

    @RequiresEdt
    private fun update(fn: ModelsDraft.() -> ModelsDraft) {
        checkEdt()
        draft = draft.fn()
        sync()
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) { "Models settings UI updates must run on EDT" }
    }
}

private const val KILO_PROVIDER = "kilo"

private fun variantTitle(value: String): String = value.replaceFirstChar { it.titlecase() }

private fun title(value: String): String = value.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.titlecase() }
