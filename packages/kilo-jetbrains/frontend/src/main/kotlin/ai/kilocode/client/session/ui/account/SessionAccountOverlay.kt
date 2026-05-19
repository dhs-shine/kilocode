package ai.kilocode.client.session.ui.account

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.controller.SessionControllerEvent
import ai.kilocode.client.ui.FilledBadgeIcon
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.PickerButton
import ai.kilocode.client.ui.RoundedContentPanel
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.DecimalFormat
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

/**
 * Compact account overlay shown in the top-right of the empty session screen.
 *
 * Displays logged-out prompt or logged-in account/balance info.
 * Visibility is controlled entirely by [onEvent] — never set [isVisible] externally.
 */
internal class SessionAccountOverlay(
    private val select: (String?) -> Unit,
    private val login: () -> Unit,
    private val profile: () -> Unit,
) : BorderLayoutPanel() {

    companion object {
        private const val CARD_OUT = "out"
        private const val CARD_IN = "in"
    }

    private val loginLabel = JBLabel(KiloBundle.message("profile.notLoggedIn")).apply {
        foreground = UiStyle.Colors.weak()
    }
    private val loginBtn = JButton(KiloBundle.message("profile.action.login")).apply {
        isOpaque = false
        addActionListener { login() }
    }
    private val outCard = JPanel(GridBagLayout()).apply {
        isOpaque = false
        add(loginLabel, GridBagConstraints().apply {
            gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
        })
        add(loginBtn, GridBagConstraints().apply {
            gridx = 0; gridy = 1; anchor = GridBagConstraints.CENTER
            insets = JBUI.insetsTop(UiStyle.Gap.sm())
        })
    }

    private val picker = PickerButton().apply {
        isEnabled = false
        text = " "
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isEnabled || choices.isEmpty()) return
                showPopup()
            }
        })
    }

    private val fmt = DecimalFormat("$#,##0.00")
    private var balanceText: String? = null

    private val balance = JBLabel().apply {
        isVisible = false
    }

    private val profileBtn = HoverIcon().apply {
        icon = AllIcons.General.User
        toolTipText = KiloBundle.message("action.Kilo.ShowProfile.description")
        accessibleContext.accessibleName = KiloBundle.message("action.Kilo.ShowProfile.text")
        addActionListener { profile() }
    }

    private val row = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        add(picker)
        add(Box.createHorizontalStrut(UiStyle.Gap.md()))
        add(balance)
        add(Box.createHorizontalStrut(UiStyle.Gap.md()))
        add(profileBtn)
    }

    private val panel = RoundedContentPanel(UiStyle.Gap.lg(), UiStyle.Gap.lg()).apply {
        addToCenter(row)
    }

    private val inCard = JPanel(GridBagLayout()).apply {
        isOpaque = false
        add(panel, GridBagConstraints().apply {
            gridx = 0; gridy = 0; fill = GridBagConstraints.HORIZONTAL
        })
    }

    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout).apply {
        isOpaque = false
        add(outCard, CARD_OUT)
        add(inCard, CARD_IN)
    }

    private var choices: List<AccountChoice> = emptyList()
    private var currentOrgId: String? = null

    init {
        isOpaque = false
        isVisible = false
        addToCenter(cards)
    }

    fun onEvent(event: SessionControllerEvent.AccountOverlayChanged) {
        var layout = false
        var paint = false
        when (event) {
            is SessionControllerEvent.AccountOverlayChanged.Hide -> {
                if (isVisible) {
                    isVisible = false
                    layout = true
                    paint = true
                }
            }
            is SessionControllerEvent.AccountOverlayChanged.Show -> {
                val snap = event.account
                val prof = snap.profile
                if (prof == null) {
                    if (!snap.transient) {
                        layout = showCard(CARD_OUT) || layout
                        if (!isVisible) {
                            isVisible = true
                            layout = true
                        }
                    }
                } else {
                    layout = updateLoggedIn(prof, snap.switching, snap.targetOrgId) || layout
                    layout = showCard(CARD_IN) || layout
                    if (!isVisible) {
                        isVisible = true
                        layout = true
                    }
                }
            }
        }
        if (layout) revalidate()
        if (layout || paint) repaint()
    }

    private fun activeCard(): String? {
        for (i in 0 until cards.componentCount) {
            val comp = cards.getComponent(i)
            if (comp.isVisible) return if (comp === inCard) CARD_IN else CARD_OUT
        }
        return null
    }

    private fun showCard(card: String): Boolean {
        if (activeCard() == card) return false
        cardLayout.show(cards, card)
        return true
    }

    private fun updateLoggedIn(prof: ai.kilocode.rpc.dto.ProfileDto, switching: Boolean, target: String?): Boolean {
        var layout = false
        val orgs = prof.organizations
        val next = listOf(AccountChoice(null, KiloBundle.message("profile.personalAccount"))) +
            orgs.map { org -> AccountChoice(org.id, org.name) }
        if (next != choices) {
            choices = next
            layout = true
        }

        if (currentOrgId != prof.currentOrgId) currentOrgId = prof.currentOrgId

        val activeId = if (switching) target else prof.currentOrgId
        val active = choices.firstOrNull { it.org == activeId } ?: choices.firstOrNull()
        val title = "${active?.title ?: " "} ▾"
        if (picker.text != title) {
            picker.text = title
            layout = true
        }

        val enabled = !switching
        if (picker.isEnabled != enabled) {
            picker.isEnabled = enabled
            picker.repaint()
        }

        val tip = if (switching) {
            KiloBundle.message("profile.switchingAccount")
        } else {
            KiloBundle.message("session.account.switcher")
        }
        if (picker.toolTipText != tip) picker.toolTipText = tip

        if (!picker.isVisible) {
            picker.isVisible = true
            layout = true
        }

        layout = syncBalance(prof) || layout
        return layout
    }

    private fun syncBalance(prof: ai.kilocode.rpc.dto.ProfileDto): Boolean {
        var layout = false
        val next = prof.balance?.let { fmt.format(it.balance) }
        if (next == null) {
            if (balance.isVisible) {
                balance.isVisible = false
                layout = true
            }
            if (balance.icon != null) {
                balance.icon = null
            }
            if (balance.toolTipText != null) balance.toolTipText = null
            balanceText = null
        } else {
            if (!balance.isVisible) {
                balance.isVisible = true
                layout = true
            }
            if (balanceText != next || balance.icon == null) {
                balance.icon = FilledBadgeIcon(
                    next,
                    UiStyle.Colors.badgeBg(),
                    UiStyle.Colors.badgeFg(),
                )
                layout = true
            }
            val tip = KiloBundle.message("session.account.balance", next)
            if (balance.toolTipText != tip) balance.toolTipText = tip
            balanceText = next
        }
        return layout
    }

    private fun showPopup() {
        val bg = UiStyle.Colors.cardBg()
        val model = CollectionListModel(choices)
        val list = JBList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            background = bg
            border = JBUI.Borders.empty(UiStyle.Gap.xs(), 0)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        list.cellRenderer = AccountPickerRenderer { currentOrgId }

        val idx = choices.indexOfFirst { it.org == currentOrgId }.takeIf { it >= 0 } ?: 0
        if (idx >= 0) {
            list.selectedIndex = idx
            ScrollingUtil.ensureIndexIsVisible(list, idx, 0)
        }

        lateinit var popup: com.intellij.openapi.ui.popup.JBPopup

        fun activate(choice: AccountChoice) {
            if (choice.org != currentOrgId) select(choice.org)
            popup.closeOk(null)
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true)) return
                val row = list.locationToIndex(e.point)
                val bounds = row.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) } ?: return
                if (!bounds.contains(e.point)) return
                activate(model.getElementAt(row))
            }
        })

        ListUtil.installAutoSelectOnMouseMove(list)
        ScrollingUtil.installActions(list)

        list.registerKeyboardAction(
            { list.selectedValue?.let(::activate) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        list.registerKeyboardAction(
            { popup.cancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_FOCUSED,
        )

        val scroll = ScrollPaneFactory.createScrollPane(list).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
            background = bg
            viewport.background = bg
            viewport.isOpaque = true
        }
        val content = RoundedContentPanel(UiStyle.Gap.sm(), UiStyle.Gap.sm()).apply {
            addToCenter(scroll)
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, list)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .setCancelOnWindowDeactivation(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()

        popup.showUnderneathOf(picker)
    }

    internal fun loggedInVisible() = isVisible && cards.let {
        var card = CARD_OUT
        for (i in 0 until it.componentCount) {
            val comp = it.getComponent(i)
            if (comp.isVisible) card = if (comp === inCard) CARD_IN else CARD_OUT
        }
        card == CARD_IN
    }

    internal fun loggedOutVisible() = isVisible && cards.let {
        for (i in 0 until it.componentCount) {
            val comp = it.getComponent(i)
            if (comp.isVisible) return@let comp === outCard
        }
        false
    }

    internal fun accountTitle(): String? = picker.text?.removeSuffix(" ▾")?.ifBlank { null }
    internal fun pickerEnabled() = picker.isEnabled
    internal fun pickerVisible() = picker.isVisible
    internal fun choiceCount() = choices.size
    internal fun selectedIndex() = choices.indexOfFirst { it.org == currentOrgId }.takeIf { it >= 0 } ?: 0
    internal fun panelBackground() = panel.background
    internal fun panelBorderColor() = UiStyle.Colors.cardBorder()
    internal fun balanceVisible() = balance.isVisible
    internal fun balanceIcon() = balance.icon
    internal fun balanceText() = balanceText
    internal fun profileIcon() = profileBtn.icon
    internal fun clickProfile() = profileBtn.doClick()
}
