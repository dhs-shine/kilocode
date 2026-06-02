package ai.kilocode.client.settings.models

import ai.kilocode.client.KiloNotifications
import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.ReasoningPicker
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.settings.profile.UserProfileConfigurable
import ai.kilocode.client.settings.profile.edt
import ai.kilocode.client.settings.ui.SettingsBannerKind
import ai.kilocode.client.settings.ui.SettingsContentPanel
import ai.kilocode.client.settings.ui.SettingsPanel
import ai.kilocode.client.settings.ui.SettingsRow
import ai.kilocode.client.settings.ui.SettingsRows
import ai.kilocode.client.ui.layout.HAlign
import ai.kilocode.client.ui.layout.VAlign
import ai.kilocode.client.ui.layout.align
import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.dto.AgentDto
import ai.kilocode.rpc.dto.ConfigPatchDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.LoadErrorDto
import ai.kilocode.rpc.dto.ModelsWorkspaceDto
import ai.kilocode.rpc.dto.ProvidersDto
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Predicate
import javax.swing.JComponent

internal class ModelsSettingsUi(
    private val cs: CoroutineScope,
    private val app: KiloAppService = service(),
    private val workspaces: KiloWorkspaceService = service(),
    private val directory: String? = null,
) : SettingsPanel() {

    companion object {
        private val LOG = KiloLog.create(ModelsSettingsUi::class.java)
    }

    private val form = ModelsSettingsContent(app, ::update, ::selectSubagent)
    private val defaults = form.defaults
    private val small = form.small
    private val subagent = form.subagent
    private val variant = form.variant
    private val variantRow = form.variantRow
    private val pickers = form.pickers
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
    private var pending: ModelsDraft? = null
    private var saveError: String? = null
    private var disposed = false

    init {
        setContent(form)
        sync()
        start()
    }

    @RequiresEdt
    fun modified(): Boolean {
        checkEdt()
        return draft != (pending ?: baseline)
    }

    @RequiresEdt
    fun resetDraft() {
        checkEdt()
        draft = pending ?: baseline
        saveError = null
        if (!saving) clearProgress()
        sync()
    }

    @RequiresEdt
    fun applyDraft() {
        checkEdt()
        val prev = baseline
        val next = draft
        val patch = patch(prev, next)
        if (patch.values.isEmpty() && patch.agents.isEmpty()) return
        LOG.info("model settings save: started ${summary(patch)}")
        pending = next
        saving = true
        saveError = null
        showProgress(KiloBundle.message("settings.models.save.pending"))
        sync()
        app.updateConfigAsync(patch) { state ->
            ApplicationManager.getApplication().invokeLater({
                if (disposed) {
                    if (state == null) {
                        LOG.warn("model settings save: failed after dispose ${summary(patch)}")
                        KiloNotifications.error(KiloBundle.message("settings.models.save.failed"))
                    } else {
                        LOG.info("model settings save: completed after dispose ${summary(patch)}")
                    }
                    return@invokeLater
                }
                if (state != null) {
                    LOG.info("model settings save: completed ${summary(patch)}")
                    val edit = draft
                    appState = state
                    val base = modelsDraft(state.config, agents)
                    baseline = if (savedMatches(base, next)) base else next
                    draft = if (edit == next) baseline else edit
                    pending = null
                    saving = false
                    saveError = null
                    clearProgress()
                    sync()
                    return@invokeLater
                }
                val edit = draft
                baseline = prev
                draft = if (edit == next) next else edit
                pending = null
                saving = false
                LOG.warn("model settings save: failed ${summary(patch)}")
                saveError = KiloBundle.message("settings.models.save.failed")
                sync()
            }, ModalityState.any())
        }
    }

    @RequiresEdt
    fun updateApp(state: KiloAppStateDto) {
        checkEdt()
        appState = state
        if (state.status != KiloAppStatusDto.READY) {
            loading = false
            if (!loaded && providers == null) {
                agents = emptyList()
                errors = emptyList()
            }
            sync()
            return
        }
        val base = modelsDraft(state.config, agents)
        acceptBase(base)
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
        acceptBase(base)
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
        disposed = true
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
        val editable = !saving && (ready || state == ModelsStatus.LOADING)
        val bannerVisible = modelsLoginBannerVisible(
            ready = appState.status == KiloAppStatusDto.READY,
            authenticated = appState.profile != null,
        )
        syncBanner(state, bannerVisible)
        val err = saveError
        if (saving || state == ModelsStatus.SAVING) {
            showProgress(KiloBundle.message("settings.models.save.pending"))
        } else if (err != null) {
            showError(err)
        } else if (state == ModelsStatus.UNAVAILABLE || state == ModelsStatus.LOADING) {
            showProgress(KiloBundle.message("settings.models.loading"))
        } else {
            clearProgress()
        }
        var layout = false
        defaults.setItems(allItems, draft.model)
        small.setItems(smallItems, draft.small)
        subagent.setItems(allItems, draft.subagent)
        listOf(defaults, small, subagent).forEach { it.isEnabled = editable }
        layout = syncVariant(editable) || layout
        layout = syncModes(editable) || layout
        if (layout) {
            revalidate()
            repaint()
        }
    }

    @RequiresEdt
    private fun syncBanner(state: ModelsStatus, login: Boolean) {
        checkEdt()
        if (login) {
            top.showNotLoggedIn { openProfile(it) }
            return
        }
        if ((saving || state == ModelsStatus.LOADING || state == ModelsStatus.SAVING) && top.isVisible) return
        when (state) {
            ModelsStatus.LOAD_FAILED -> top.showBanner(
                KiloBundle.message("settings.models.load.failed"),
                emptyList(),
                SettingsBannerKind.ERROR,
            )
            ModelsStatus.NO_PROVIDERS -> top.showBanner(KiloBundle.message("settings.models.noProviders"), emptyList())
            ModelsStatus.MODES_FAILED -> top.showBanner(KiloBundle.message("settings.models.modes.failed"), emptyList())
            else -> top.hideBanner()
        }
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
    private fun syncVariant(ready: Boolean): Boolean {
        val item = allItems.firstOrNull { it.key == draft.subagent || it.id == draft.subagent }
        val valid = item?.variants.orEmpty()
        if (draft.variant != null && draft.variant !in valid) draft = draft.copy(variant = valid.firstOrNull())
        if (draft.subagent != null && valid.isEmpty() && draft.variant != null) draft = draft.copy(variant = null)
        variant.setItems(valid.map { ReasoningPicker.Item(it, variantTitle(it)) }, draft.variant)
        variant.isEnabled = ready && valid.isNotEmpty()
        val visible = valid.isNotEmpty()
        val changed = variantRow.isVisible != visible
        variantRow.isVisible = visible
        variant.isVisible = visible
        return changed
    }

    @RequiresEdt
    private fun syncModes(ready: Boolean): Boolean {
        var layout = false
        val names = agents.map { it.name }
        if (names != pickers.keys.toList()) {
            form.modes.removeAll()
            pickers.clear()
            agents.forEach { agent ->
                val picker = ModelSettingPicker()
                picker.picker.favorites = { app.favorites.value }
                picker.picker.onFavoriteToggle = { app.toggleModelFavorite(it.provider, it.id) }
                picker.picker.onSelect = { item -> update { copy(agents = this.agents + (agent.name to item.key)) } }
                picker.picker.onClear = { update { copy(agents = this.agents + (agent.name to null)) } }
                pickers[agent.name] = picker
                form.modes.row(agent.name, SettingsRow(
                    agent.displayName ?: title(agent.name),
                    agent.description,
                    picker,
                ))
            }
            layout = true
        }
        agents.forEach { agent ->
            val name = agent.name
            val picker = pickers[name] ?: return@forEach
            form.modes.update(name, agent.displayName ?: title(name), agent.description, picker)
            val value = draft.agents[name]
            picker.setItems(allItems, value)
            picker.isEnabled = ready
        }
        return layout
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
        saveError = null
        sync()
    }

    private fun acceptBase(base: ModelsDraft) {
        val save = pending
        if (save == null) {
            val prev = baseline
            val edit = draft
            baseline = base
            if (edit == prev) draft = base
            return
        }
        if (!savedMatches(base, save)) return
        baseline = base
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) { "Models settings UI updates must run on EDT" }
    }
}

private const val KILO_PROVIDER = "kilo"

private fun summary(patch: ConfigPatchDto): String {
    val values = patch.values.keys.sorted().joinToString(",").ifEmpty { "none" }
    return "values=$values agents=${patch.agents.size}"
}

private class ModelsSettingsContent(
    app: KiloAppService,
    update: (ModelsDraft.() -> ModelsDraft) -> Unit,
    select: (ModelPicker.Item) -> Unit,
) : SettingsContentPanel() {
    val defaults = ModelSettingPicker()
    val small = ModelSettingPicker()
    val subagent = ModelSettingPicker()
    val variant = ReasoningPicker()
    val variantRow = SettingsRow(
        KiloBundle.message("settings.models.subagentVariant.title"),
        KiloBundle.message("settings.models.subagentVariant.description"),
        variant.align(HAlign.RIGHT, VAlign.CENTER),
    )
    val modes: SettingsRows
    val pickers = linkedMapOf<String, ModelSettingPicker>()

    init {
        defaults.picker.onSelect = { update { copy(model = it.key) } }
        defaults.picker.onClear = { update { copy(model = null) } }
        small.picker.onSelect = { update { copy(small = it.key) } }
        small.picker.onClear = { update { copy(small = null) } }
        small.picker.includeSmall = true
        subagent.picker.onSelect = { item -> select(item) }
        subagent.picker.onClear = { update { copy(subagent = null, variant = null) } }
        variant.onSelect = { item -> update { copy(variant = item.id) } }
        listOf(defaults, small, subagent).forEach { picker ->
            picker.picker.favorites = { app.favorites.value }
            picker.picker.onFavoriteToggle = { app.toggleModelFavorite(it.provider, it.id) }
        }

        val rows = section(KiloBundle.message("settings.models.displayName"))
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
        rows.row(variantRow)
        modes = section(
            KiloBundle.message("settings.models.modeModels.title"),
            KiloBundle.message("settings.models.modeModels.description"),
        )
    }
}

private fun variantTitle(value: String): String = value.replaceFirstChar { it.titlecase() }

private fun title(value: String): String = value.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.titlecase() }
