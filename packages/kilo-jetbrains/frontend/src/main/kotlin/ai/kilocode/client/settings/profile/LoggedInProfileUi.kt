package ai.kilocode.client.settings.profile

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ProfileDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.RelativeFont
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.text.DecimalFormat
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
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
) : BorderLayoutPanel() {

    private val nameLabel = JBLabel().also { RelativeFont.BOLD.install(it) }
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

    private val balanceCard = BorderLayoutPanel().apply {
        border = JBUI.Borders.compound(
            RoundedLineBorder(JBColor.border(), UiStyle.Arc.component()),
            JBUI.Borders.empty(UiStyle.Gap.pad(), UiStyle.Gap.xl()),
        )
        addToTop(titleLabel)
        addToCenter(JPanel(GridBagLayout()).apply {
            add(valueLabel, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.CENTER
            })
            add(refreshBtn, GridBagConstraints().apply {
                gridx = 0; gridy = 1; anchor = GridBagConstraints.CENTER
                insets = JBUI.insetsTop(UiStyle.Gap.pad())
            })
        })
    }

    private val comboModel = DefaultComboBoxModel<String>()
    val combo = ComboBox(comboModel)

    val dashboardBtn = JButton(KiloBundle.message("profile.action.dashboard"))
        .also { it.addActionListener { dashboard() } }
    val logoutBtn = JButton(KiloBundle.message("profile.action.logout"))
        .also { it.addActionListener { logout() } }

    private val actionRow = JPanel(GridBagLayout()).apply {
        add(dashboardBtn, GridBagConstraints().apply {
            gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
        })
        add(logoutBtn, GridBagConstraints().apply {
            gridx = 1; gridy = 0; anchor = GridBagConstraints.WEST
            insets = JBUI.insetsLeft(UiStyle.Gap.md())
        })
    }

    private val rows: List<java.awt.Component> = listOf(nameLabel, emailLabel, combo, balanceCard, actionRow)

    private val content = JPanel(GridBagLayout()).apply {
        val gap = UiStyle.Gap.lg()
        rows.forEachIndexed { i, comp ->
            add(comp, GridBagConstraints().apply {
                gridx = 0; gridy = i
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                insets = if (i == 0) JBUI.emptyInsets() else JBUI.insetsTop(gap)
            })
        }
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
        addToTop(content)
    }

    fun update(profile: ProfileDto, accounts: Boolean = true) {
        currentProf = profile

        val display = profile.name?.takeIf { it.isNotBlank() } ?: profile.email
        if (nameLabel.text != display) nameLabel.text = display

        val showEmail = profile.name != null
        if (emailLabel.isVisible != showEmail) emailLabel.isVisible = showEmail
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
        if (this.refreshing == refreshing) return
        this.refreshing = refreshing
        val text = if (refreshing) KiloBundle.message("profile.action.refreshing")
        else KiloBundle.message("profile.action.refresh")
        if (refreshBtn.text != text) refreshBtn.text = text
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
            syncLayout()
        }
    }
}
