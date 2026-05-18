package ai.kilocode.client.settings.profile

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.DeviceAuthDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel

internal enum class OutMode { CONNECTING, ERROR, AUTH, EMPTY }

/**
 * Retained logged-out UI. Internally uses a [CardLayout] to switch between
 * connecting, error, device-auth, and not-logged-in states without rebuilding.
 */
internal class LoggedOutProfileUi(
    private val login: () -> Unit,
    private val retry: () -> Unit,
    private val cancel: () -> Unit,
    private val browse: (String) -> Unit,
) : JPanel(BorderLayout()) {

    private val cards = JPanel(CardLayout())
    private val cardLayout = cards.layout as CardLayout
    private var mode: OutMode? = null

    // -- retained components for connecting card --
    private val retryBtnConnecting = JButton(KiloBundle.message("profile.action.retry"))
        .also { it.addActionListener { retry() } }

    private val connectingCard = panel {
        row {
            label(KiloBundle.message("profile.status.connecting"))
                .applyToComponent { foreground = UiStyle.Colors.weak() }
        }
        row { cell(retryBtnConnecting) }
    }

    // -- retained components for error card --
    private val retryBtnError = JButton(KiloBundle.message("profile.action.retry"))
        .also { it.addActionListener { retry() } }

    private val errorCard = panel {
        row {
            label(KiloBundle.message("profile.status.error"))
                .applyToComponent { foreground = UiStyle.Colors.errorLabelForeground() }
        }
        row { cell(retryBtnError) }
    }

    // -- retained components for not-logged-in card --
    val loginBtn = JButton(KiloBundle.message("profile.action.login"))
        .also { it.addActionListener { login() } }

    private val emptyCard = panel {
        row {
            label(KiloBundle.message("profile.notLoggedIn"))
                .applyToComponent { foreground = UiStyle.Colors.weak() }
        }
        row { cell(loginBtn) }
    }

    // -- retained components for device-auth card --
    private val authUrlLabel = JBLabel().apply { setCopyable(true) }
    private val authCodeLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
    private val authCodeRowPanel = JPanel()
    private val cancelBtn = JButton(KiloBundle.message("profile.login.cancel"))
        .also { it.addActionListener { cancel() } }

    private var authUrl: String? = null

    private val authCard = panel {
        row {
            label(KiloBundle.message("profile.login.signingIn")).bold()
        }
        row(KiloBundle.message("profile.login.urlLabel")) {
            cell(authUrlLabel.also {
                it.addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        val url = authUrl ?: return
                        browse(url)
                    }
                })
            })
        }
        row(KiloBundle.message("profile.login.codeLabel")) {
            cell(authCodeRowPanel.also { it.add(authCodeLabel) })
        }
        row {
            label(KiloBundle.message("profile.login.waiting"))
                .applyToComponent { foreground = UiStyle.Colors.weak() }
        }
        row { cell(cancelBtn) }
    }

    init {
        cards.add(connectingCard, OutMode.CONNECTING.name)
        cards.add(errorCard, OutMode.ERROR.name)
        cards.add(emptyCard, OutMode.EMPTY.name)
        cards.add(authCard, OutMode.AUTH.name)
        add(cards, BorderLayout.NORTH)
    }

    fun update(status: KiloAppStatusDto, auth: DeviceAuthDto?) {
        val target = when {
            status == KiloAppStatusDto.DISCONNECTED || status == KiloAppStatusDto.CONNECTING -> OutMode.CONNECTING
            status == KiloAppStatusDto.ERROR -> OutMode.ERROR
            auth != null -> OutMode.AUTH
            else -> OutMode.EMPTY
        }

        if (target == OutMode.AUTH && auth != null) {
            authUrl = auth.verificationUrl
            authUrlLabel.text = auth.verificationUrl
            val code = auth.code
            authCodeLabel.text = code ?: ""
            authCodeRowPanel.isVisible = code != null
        }

        if (mode != target) {
            cardLayout.show(cards, target.name)
            mode = target
            revalidate()
            repaint()
        }
    }
}
