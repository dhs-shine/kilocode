package ai.kilocode.client.settings

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.DeviceAuthDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ProfileDto
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import javax.swing.JComponent
import javax.swing.JPanel

private const val DASHBOARD_URL = "https://app.kilo.ai/profile"

private val edt = Dispatchers.EDT + ModalityState.any().asContextElement()

/**
 * Settings panel for Kilo user profile.
 *
 * Located at Settings -> Tools -> Kilo -> User Profile.
 *
 * Shows login / logout, current balance, personal/org account selector,
 * and a link to the Kilo dashboard. This is a status/action panel — it
 * has no persistent settings, so [isModified] always returns false.
 */
class UserProfileConfigurable : SearchableConfigurable {

    private var ui: JComponent? = null
    private var scope: CoroutineScope? = null
    private var watchJob: Job? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = KiloBundle.message("settings.profile.displayName")

    override fun createComponent(): JComponent {
        val cs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = cs
        val panel = buildPanel(cs)
        ui = panel
        startWatching(cs, panel)
        return panel
    }

    private fun buildPanel(cs: CoroutineScope): ProfilePanel {
        val app = service<KiloAppService>()
        return ProfilePanel(app.state.value.profile, app.state.value.status, cs)
    }

    private fun startWatching(cs: CoroutineScope, panel: ProfilePanel) {
        val app = service<KiloAppService>()
        watchJob = cs.launch {
            app.state.collect { state ->
                withContext(edt) {
                    panel.update(state.profile, state.status)
                }
            }
        }
        cs.launch {
            app.connect()
        }
    }

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    override fun reset() = Unit

    override fun disposeUIResources() {
        watchJob?.cancel()
        watchJob = null
        scope?.cancel()
        scope = null
        ui = null
    }

    companion object {
        const val ID = "ai.kilocode.jetbrains.settings.profile"
    }
}

/**
 * Retained Swing panel for the User Profile settings page.
 *
 * Re-renders content in response to [update] calls from the app state watcher.
 * Does not rebuild the root component tree — replaces only the inner content panel.
 */
internal class ProfilePanel(
    profile: ProfileDto?,
    status: KiloAppStatusDto,
    private val cs: CoroutineScope,
    private val app: KiloAppService = service(),
    private val browse: (String) -> Unit = { BrowserUtil.browse(it) },
) : JPanel() {

    private var prof = profile
    private var status = status
    private var auth: DeviceAuthDto? = null

    init {
        layout = java.awt.BorderLayout()
        sync()
    }

    fun update(profile: ProfileDto?, status: KiloAppStatusDto) {
        checkEdt()
        prof = profile
        this.status = status
        sync()
    }

    private fun sync() {
        checkEdt()
        removeAll()
        add(buildContent(), java.awt.BorderLayout.NORTH)
        revalidate()
        repaint()
    }

    private fun applyState() {
        checkEdt()
        val state = app.state.value
        update(state.profile, state.status)
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) {
            "ProfilePanel UI updates must run on EDT"
        }
    }

    private fun buildContent(): JComponent = panel {
        val current = status
        val profile = prof
        when {
            current == KiloAppStatusDto.DISCONNECTED || current == KiloAppStatusDto.CONNECTING -> {
                row {
                    label(KiloBundle.message("profile.status.connecting"))
                        .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
                }
                row {
                    button(KiloBundle.message("profile.action.retry")) {
                        app.retryAsync()
                    }
                }
            }
            current == KiloAppStatusDto.ERROR -> {
                row {
                    label(KiloBundle.message("profile.status.error"))
                        .applyToComponent { foreground = UIUtil.getErrorForeground() }
                }
                row {
                    button(KiloBundle.message("profile.action.retry")) {
                        app.retryAsync()
                    }
                }
            }
            profile == null -> {
                val a = auth
                if (a != null) buildDeviceAuthSection(a)
                else buildLoggedOutSection()
            }
            else -> buildLoggedInSection(profile)
        }
    }

    private fun Panel.buildLoggedOutSection() {
        row {
            label(KiloBundle.message("profile.notLoggedIn"))
                .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
        }
        row {
            button(KiloBundle.message("profile.action.login")) {
                startLoginFlow()
            }
        }
    }

    private fun Panel.buildDeviceAuthSection(deviceAuth: DeviceAuthDto) {
        row {
            label(KiloBundle.message("profile.login.signingIn")).bold()
        }
        row(KiloBundle.message("profile.login.urlLabel")) {
            browserLink(deviceAuth.verificationUrl, deviceAuth.verificationUrl)
        }
        val code = deviceAuth.code
        if (code != null) {
            row(KiloBundle.message("profile.login.codeLabel")) {
                label(code).bold()
            }
        }
        row {
            label(KiloBundle.message("profile.login.waiting"))
                .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
        }
        row {
            button(KiloBundle.message("profile.login.cancel")) {
                auth = null
                sync()
            }
        }
    }

    private fun Panel.buildLoggedInSection(profile: ProfileDto) {
        // User info
        group(KiloBundle.message("profile.group.account")) {
            row {
                val name = profile.name?.takeIf { it.isNotBlank() } ?: profile.email
                label(name).bold()
            }
            if (profile.name != null) {
                row {
                    label(profile.email)
                        .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
                }
            }
        }

        // Balance
        profile.balance?.let { bal ->
            val fmt = DecimalFormat("$#,##0.00")
            row {
                label(KiloBundle.message("profile.balance.title"))
                    .gap(RightGap.SMALL)
                label(fmt.format(bal.balance)).bold()
            }.topGap(TopGap.SMALL)
        }

        // Organization selector
        val orgs = profile.organizations
        if (orgs.isNotEmpty()) {
            group(KiloBundle.message("profile.group.organization")) {
                val options = listOf(KiloBundle.message("profile.personalAccount")) +
                        orgs.map { "${it.name} (${it.role.lowercase()})" }
                val current = profile.currentOrgId
                    ?.let { id -> orgs.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
                    ?: 0
                row(KiloBundle.message("profile.label.account")) {
                    comboBox(options)
                        .applyToComponent { selectedIndex = current }
                        .align(AlignX.FILL)
                        .onChanged { combo ->
                            val idx = combo.selectedIndex
                            val orgId = if (idx == 0) null else orgs[idx - 1].id
                            organization(orgId)
                        }
                }
            }
        }

        // Action buttons
        row {
            button(KiloBundle.message("profile.action.dashboard")) {
                browse(DASHBOARD_URL)
            }.gap(RightGap.SMALL)
            button(KiloBundle.message("profile.action.logout")) {
                logout()
            }
        }.bottomGap(BottomGap.SMALL)
    }

    private fun startLoginFlow() {
        cs.launch {
            try {
                val next = app.startLogin()
                withContext(edt) {
                    auth = next
                    sync()
                    browse(next.verificationUrl)
                }

                val profile = app.completeLogin()
                val state = app.state.value
                withContext(edt) {
                    auth = null
                    update(profile ?: state.profile, state.status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(edt) {
                    auth = null
                    applyState()
                }
            }
        }
    }

    private fun logout() {
        cs.launch {
            try {
                val ok = app.logout()
                if (!ok) return@launch
                val state = app.state.value
                withContext(edt) {
                    auth = null
                    update(state.profile, state.status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(edt) {
                    applyState()
                }
            }
        }
    }

    private fun organization(org: String?) {
        cs.launch {
            try {
                val profile = app.setOrganization(org)
                val state = app.state.value
                withContext(edt) {
                    update(profile ?: state.profile, state.status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(edt) {
                    applyState()
                }
            }
        }
    }
}
