package ai.kilocode.client.settings.base

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ModelStateDto
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal abstract class BaseWorkspaceSettingsUi<C : BaseContentPanel, D, P, R, W>(
    cs: CoroutineScope,
    initial: D,
    private val app: KiloAppService = service(),
    private val workspaces: KiloWorkspaceService = service(),
    private val hint: String? = null,
    loginBanner: Boolean = true,
) : BaseSettingsUi<C, D, P, R>(cs, initial, loginBanner) {
    protected var appState: KiloAppStateDto = app.state.value
        private set
    protected var modelState: ModelStateDto = app.models.value
        private set
    protected var projectDirectory: String? = null
        private set
    protected val hasProjectDirectory get() = projectDirectory != null || hint != null
    protected var workspaceLoading = false
        private set
    protected var workspaceLoaded = false
        private set

    @RequiresEdt
    protected fun startSettings(content: C) {
        setSettingsContent(content)
        syncContent()
        start()
    }

    private fun start() {
        jobs += scope.launch {
            app.state.collect { state -> withContext(edt) { updateApp(state) } }
        }
        jobs += scope.launch {
            app.models.collect { state -> withContext(edt) { updateModels(state) } }
        }
        jobs += scope.launch { app.connect() }
        val path = hint ?: return
        jobs += scope.launch {
            val dir = workspaces.resolveProjectDirectory(path)
            withContext(edt) {
                projectDirectory = dir
                workspaceLoaded = false
                syncContent()
                load()
            }
        }
    }

    @RequiresEdt
    private fun updateApp(state: KiloAppStateDto) {
        appState = state
        if (state.status != KiloAppStatusDto.READY) {
            workspaceLoading = false
            unavailable(state)
            syncContent()
            return
        }
        acceptBase(draft(state))
        syncContent()
        load()
    }

    @RequiresEdt
    private fun updateModels(state: ModelStateDto) {
        modelState = state
        models(state)
        syncContent()
    }

    @RequiresEdt
    private fun load() {
        val root = projectDirectory ?: return
        if (appState.status != KiloAppStatusDto.READY || workspaceLoading || workspaceLoaded) return
        workspaceLoading = true
        clearWorkspaceError()
        syncContent()
        jobs += scope.launch {
            val state = loadWorkspace(root)
            withContext(edt) {
                applyWorkspace(state)
                workspaceLoaded = true
                workspaceLoading = false
                acceptBase(draft(appState))
                syncContent()
            }
        }
    }

    @RequiresEdt
    protected abstract fun draft(state: KiloAppStateDto): D

    @RequiresBackgroundThread
    protected abstract suspend fun loadWorkspace(root: String): W

    @RequiresEdt
    protected abstract fun applyWorkspace(result: W)

    @RequiresEdt
    protected open fun unavailable(state: KiloAppStateDto) = Unit

    @RequiresEdt
    protected open fun models(state: ModelStateDto) = Unit

    @RequiresEdt
    protected open fun clearWorkspaceError() = Unit

    private companion object {
        val edt = Dispatchers.EDT + ModalityState.any().asContextElement()
    }
}
