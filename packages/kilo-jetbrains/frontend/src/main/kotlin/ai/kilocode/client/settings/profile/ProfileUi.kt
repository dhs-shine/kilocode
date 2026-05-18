package ai.kilocode.client.settings.profile

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ProfileDto
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

internal const val DASHBOARD_URL = "https://app.kilo.ai/profile"

internal val edt = Dispatchers.EDT + ModalityState.any().asContextElement()

private enum class Card { OUT, IN }

/**
 * Retained top-level profile UI component.
 *
 * Builds [LoggedOutProfileUi] and [LoggedInProfileUi] once and switches between them
 * using a [CardLayout] — no [removeAll] or panel rebuilds on state changes.
 */
internal class ProfileUi(
    profile: ProfileDto?,
    status: KiloAppStatusDto,
    private val cs: CoroutineScope,
    private val app: KiloAppService = service(),
    private val browse: (String) -> Unit = { BrowserUtil.browse(it) },
) : JPanel(BorderLayout()) {

    private val cards = JPanel(CardLayout())
    private val cardLayout = cards.layout as CardLayout

    private val out = LoggedOutProfileUi(
        login = ::start,
        retry = { app.retryAsync() },
        cancel = ::cancel,
        browse = browse,
    )
    private val account = LoggedInProfileUi(
        dashboard = { browse(DASHBOARD_URL) },
        logout = ::logout,
        organization = ::organization,
        refresh = ::refreshProfile,
    )

    private var prof = profile
    private var status = status
    private var login: LoginState = LoginState.Idle
    private var attempt = 0
    private var card: Card? = null
    private var switching = false

    init {
        cards.add(out, Card.OUT.name)
        cards.add(account, Card.IN.name)
        add(cards, BorderLayout.NORTH)
        sync()
    }

    fun update(profile: ProfileDto?, status: KiloAppStatusDto, accounts: Boolean = true) {
        checkEdt()
        this.status = status
        val was = switching
        if (profile != null) {
            prof = profile
            login = LoginState.Idle
            this.switching = false
        } else if (!was || prof == null) {
            prof = null
        }
        sync(accounts && !(was && profile == null))
    }

    private fun sync(accounts: Boolean = true) {
        checkEdt()
        val target = targetCard()
        if (target == Card.OUT) {
            out.update(status, login)
        } else {
            account.update(prof!!, accounts)
        }
        if (card != target) {
            cardLayout.show(cards, target.name)
            card = target
            revalidate()
            repaint()
        }
    }

    private fun targetCard(): Card {
        val s = status
        val p = prof
        return when {
            s == KiloAppStatusDto.DISCONNECTED || s == KiloAppStatusDto.CONNECTING -> Card.OUT
            s == KiloAppStatusDto.ERROR -> Card.OUT
            p == null -> Card.OUT
            else -> Card.IN
        }
    }

    private fun applyState() {
        checkEdt()
        val state = app.state.value
        update(state.profile, state.status)
    }

    private fun checkEdt() {
        check(ApplicationManager.getApplication().isDispatchThread) {
            "ProfileUi updates must run on EDT"
        }
    }

    private fun start() {
        val id = ++attempt
        login = LoginState.Initiating
        sync()
        cs.launch {
            try {
                val next = app.startLogin()
                withContext(edt) {
                    if (id != attempt) return@withContext
                    login = LoginState.Pending(next, System.currentTimeMillis())
                    sync()
                    browse(next.verificationUrl)
                }
                val profile = app.completeLogin()
                val state = app.state.value
                withContext(edt) {
                    if (id != attempt) return@withContext
                    login = LoginState.Idle
                    update(profile ?: state.profile, state.status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(edt) {
                    if (id != attempt) return@withContext
                    login = LoginState.Error(compactLoginError(e))
                    sync()
                }
            }
        }
    }

    private fun cancel() {
        attempt++
        login = LoginState.Idle
        sync()
    }

    private fun logout() {
        cs.launch {
            try {
                val ok = app.logout()
                if (!ok) return@launch
                val state = app.state.value
                withContext(edt) {
                    login = LoginState.Idle
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
        switching = true
        cs.launch {
            try {
                val profile = app.setOrganization(org)
                val state = app.state.value
                withContext(edt) {
                    switching = false
                    update(profile ?: state.profile, state.status, accounts = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(edt) {
                    switching = false
                    applyState()
                }
            }
        }
    }

    private fun refreshProfile() {
        cs.launch {
            try {
                val profile = app.refreshProfile()
                val state = app.state.value
                withContext(edt) {
                    update(profile ?: state.profile, state.status)
                    account.setRefreshing(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(edt) {
                    applyState()
                    account.setRefreshing(false)
                }
            }
        }
    }
}

private val HTML_MARKERS = listOf("<!doctype html", "<html", "<head", "<body")
private val HTTP_STATUS_RE = Regex("""(?:^|\s)([45]\d{2})(?:\s|$)""")

internal fun compactLoginError(e: Exception): String {
    val msg = e.message?.trim() ?: return KiloBundle.message("profile.login.failed")
    val lower = msg.lowercase()
    if (HTML_MARKERS.any { lower.contains(it) }) {
        val status = HTTP_STATUS_RE.find(msg)?.groupValues?.getOrNull(1)
        return if (status != null) "${KiloBundle.message("profile.login.failed")} ($status)"
        else KiloBundle.message("profile.login.failed")
    }
    val norm = msg.replace(Regex("\\s+"), " ")
    val summary = norm.take(180)
    return if (summary.isNotBlank()) summary else KiloBundle.message("profile.login.failed")
}
