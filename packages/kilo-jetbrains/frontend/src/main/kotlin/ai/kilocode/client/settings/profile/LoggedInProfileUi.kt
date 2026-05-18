package ai.kilocode.client.settings.profile

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ProfileDto
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.text.DecimalFormat
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Retained logged-in UI. Labels, combo box, and buttons are built once and
 * mutated in [update] — no component rebuilding.
 */
internal class LoggedInProfileUi(
    private val dashboard: () -> Unit,
    private val logout: () -> Unit,
    private val organization: (String?) -> Unit,
    private val refresh: () -> Unit,
) : JPanel(BorderLayout()) {

    private val nameLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
    private val emailLabel = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        setCopyable(true)
    }

    private val titleLabel = JBLabel(KiloBundle.message("profile.balance.title")).apply {
        foreground = UiStyle.Colors.weak()
    }
    private val valueLabel = JBLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        font = JBFont.h1().asBold()
    }
    private val refreshBtn = JButton(KiloBundle.message("profile.action.refresh"), AllIcons.Actions.Refresh)
        .also {
            it.addActionListener {
                if (refreshing) return@addActionListener
                setRefreshing(true)
                refresh()
            }
        }

    private val balanceCard = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            RoundedLineBorder(JBColor.border(), JBValue.UIInteger("Component.arc", 8).get()),
            JBUI.Borders.empty(JBUI.scale(12), JBUI.scale(16)),
        )
        add(titleLabel, BorderLayout.NORTH)
        add(JPanel(GridBagLayout()).apply {
            add(valueLabel, GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.CENTER
            })
            add(refreshBtn, GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                anchor = GridBagConstraints.CENTER
                insets = JBUI.insetsTop(UiStyle.Gap.pad())
            })
        }, BorderLayout.CENTER)
    }

    private val comboModel = DefaultComboBoxModel<String>()
    val combo = JComboBox(comboModel)

    val dashboardBtn = JButton(KiloBundle.message("profile.action.dashboard"))
        .also { it.addActionListener { dashboard() } }
    val logoutBtn = JButton(KiloBundle.message("profile.action.logout"))
        .also { it.addActionListener { logout() } }

    private val buttons = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
        add(dashboardBtn)
        add(Box.createHorizontalStrut(JBUI.scale(6)))
        add(logoutBtn)
    }

    private val content = JPanel(GridBagLayout()).apply {
        addRow(nameLabel, 0)
        addRow(emailLabel, 1, UiStyle.Gap.lg())
        addRow(combo, 2, UiStyle.Gap.lg())
        addRow(balanceCard, 3, UiStyle.Gap.lg())
        addRow(buttons, 4, UiStyle.Gap.lg())
    }

    private var applying = false
    private var refreshing = false
    private var currentProf: ProfileDto? = null

    init {
        combo.addActionListener {
            if (applying) return@addActionListener
            val prof = currentProf ?: return@addActionListener
            val idx = combo.selectedIndex
            if (idx < 0) return@addActionListener
            val orgId = if (idx == 0) null else prof.organizations.getOrNull(idx - 1)?.id ?: return@addActionListener
            val current = prof.currentOrgId
            if (orgId == current) return@addActionListener
            organization(orgId)
        }
        add(content, BorderLayout.NORTH)
    }

    private fun JPanel.addRow(comp: java.awt.Component, y: Int, top: Int = 0) {
        add(comp, GridBagConstraints().apply {
            gridx = 0
            gridy = y
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(top, 0, 0, 0)
        })
    }

    fun update(profile: ProfileDto, accounts: Boolean = true) {
        currentProf = profile

        val display = profile.name?.takeIf { it.isNotBlank() } ?: profile.email
        if (nameLabel.text != display) nameLabel.text = display

        val showEmail = profile.name != null
        emailLabel.isVisible = showEmail
        if (showEmail && emailLabel.text != profile.email) emailLabel.text = profile.email

        val bal = profile.balance
        var changed = false
        if (bal != null) {
            val fmt = DecimalFormat("$#,##0.00")
            val balText = fmt.format(bal.balance)
            if (valueLabel.text != balText) {
                valueLabel.text = balText
                changed = true
            }
            if (!balanceCard.isVisible) {
                balanceCard.isVisible = true
                changed = true
            }
        } else {
            if (balanceCard.isVisible) {
                balanceCard.isVisible = false
                changed = true
            }
        }

        if (accounts) applyOrganizations(profile)
        if (changed) syncLayout()
    }

    fun setRefreshing(refreshing: Boolean) {
        this.refreshing = refreshing
        val text = if (refreshing) {
            KiloBundle.message("profile.action.refreshing")
        } else {
            KiloBundle.message("profile.action.refresh")
        }
        if (refreshBtn.text != text) refreshBtn.text = text
        refreshBtn.maximumSize = refreshBtn.preferredSize
        syncLayout()
    }

    private fun syncLayout() {
        balanceCard.revalidate()
        content.revalidate()
        revalidate()
        repaint()
    }

    private fun applyOrganizations(profile: ProfileDto) {
        val orgs = profile.organizations
        val options = listOf(KiloBundle.message("profile.personalAccount")) +
                orgs.map { it.name }

        val target = profile.currentOrgId
            ?.let { id -> orgs.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
            ?: 0

        applying = true
        try {
            val existing = (0 until comboModel.size).map { comboModel.getElementAt(it) }
            if (existing != options) {
                comboModel.removeAllElements()
                options.forEach { comboModel.addElement(it) }
            }
            if (combo.selectedIndex != target) combo.selectedIndex = target
        } finally {
            applying = false
        }

        val show = orgs.isNotEmpty()
        if (combo.isVisible != show) {
            combo.isVisible = show
            revalidate()
            repaint()
        }
    }
}
