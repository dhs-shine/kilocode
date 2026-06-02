package ai.kilocode.client.settings.base

import ai.kilocode.client.KiloNotifications
import ai.kilocode.client.settings.profile.UserProfileConfigurable
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.util.function.Predicate
import javax.swing.JComponent

internal abstract class BaseSettingsUi<C : BaseContentPanel, D, P, R>(
    private val cs: CoroutineScope,
    initial: D,
    private val loginBanner: Boolean = true,
) : SettingsPanel() {
    protected lateinit var form: C
        private set
    protected val jobs = mutableListOf<Job>()
    protected var draft = initial
    protected val saving get() = save
    protected val saveError get() = error

    private var baseline = initial
    private var pending: D? = null
    private var save = false
    private var error: String? = null
    private var disposed = false

    protected fun setSettingsContent(content: C) {
        form = content
        setContent(content)
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
        error = null
        if (!save) clearProgress()
        syncContent()
    }

    @RequiresEdt
    fun applyDraft() {
        checkEdt()
        val prev = baseline
        val next = draft
        val change = change(prev, next) ?: return
        logSaveStarted(change)
        pending = next
        save = true
        error = null
        showProgress(pendingText())
        syncContent()
        save(change) { result ->
            ApplicationManager.getApplication().invokeLater({
                if (disposed) {
                    if (result == null) {
                        logSaveFailedAfterDispose(change)
                        onSaveFailedAfterDispose(change)
                    } else {
                        logSaveCompletedAfterDispose(change)
                    }
                    return@invokeLater
                }
                if (result != null) {
                    logSaveCompleted(change)
                    val edit = draft
                    val base = base(result)
                    baseline = if (saved(base, next)) base else next
                    draft = if (edit == next) baseline else edit
                    pending = null
                    save = false
                    error = null
                    clearProgress()
                    syncContent()
                    return@invokeLater
                }
                val edit = draft
                baseline = prev
                draft = if (edit == next) next else edit
                pending = null
                save = false
                error = failedText()
                logSaveFailed(change)
                syncContent()
            }, ModalityState.any())
        }
    }

    @RequiresEdt
    fun dispose() {
        checkEdt()
        disposed = true
        jobs.forEach { it.cancel() }
        jobs.clear()
        cs.cancel()
    }

    @RequiresEdt
    protected fun updateDraft(fn: D.() -> D) {
        checkEdt()
        draft = draft.fn()
        error = null
        syncContent()
    }

    protected fun acceptBase(base: D) {
        val target = pending
        if (target == null) {
            val prev = baseline
            val edit = draft
            baseline = base
            if (edit == prev) draft = base
            return
        }
        if (!saved(base, target)) return
        baseline = base
    }

    @RequiresEdt
    protected fun syncLoginBanner(login: Boolean, fallback: () -> Unit) {
        checkEdt()
        if (loginBanner && login) {
            top.showNotLoggedIn { openProfile(it) }
            return
        }
        fallback()
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) { "Settings UI updates must run on EDT" }
    }

    protected abstract fun change(from: D, to: D): P?
    protected abstract fun save(change: P, done: (R?) -> Unit)
    protected abstract fun base(result: R): D
    protected abstract fun syncContent()
    protected abstract fun pendingText(): String
    protected abstract fun failedText(): String

    protected open fun saved(base: D, draft: D): Boolean = base == draft
    protected open fun onSaveFailedAfterDispose(change: P) = KiloNotifications.error(failedText())
    protected open fun logSaveStarted(change: P) = Unit
    protected open fun logSaveCompleted(change: P) = Unit
    protected open fun logSaveFailed(change: P) = Unit
    protected open fun logSaveFailedAfterDispose(change: P) = Unit
    protected open fun logSaveCompletedAfterDispose(change: P) = Unit

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
}
