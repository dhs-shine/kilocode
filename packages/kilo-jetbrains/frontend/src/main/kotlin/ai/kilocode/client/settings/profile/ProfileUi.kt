package ai.kilocode.client.settings.profile

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.rpc.dto.DeviceAuthDto
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
    )

    private var prof = profile
    private var status = status
    private var auth: DeviceAuthDto? = null
    private var card: Card? = null
    private var switching = false

    init {
        cards.add(out, Card.OUT.name)
        cards.add(account, Card.IN.name)
        add(cards, BorderLayout.NORTH)
        sync()
    }

    fun update(profile: ProfileDto?, status: KiloAppStatusDto) {
        checkEdt()
        this.status = status
        if (profile != null) {
            prof = profile
            auth = null
            switching = false
        } else if (!switching || prof == null) {
            prof = null
        }
        sync()
    }

    private fun sync() {
        checkEdt()
        val target = targetCard()
        if (target == Card.OUT) {
            out.update(status, auth)
        } else {
            account.update(prof!!)
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
        switching = true
        cs.launch {
            try {
                val profile = app.setOrganization(org)
                val state = app.state.value
                withContext(edt) {
                    switching = false
                    update(profile ?: state.profile, state.status)
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

    private fun cancel() {
        auth = null
        sync()
    }
}
