package ai.kilocode.client.settings.profile

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ProfileDto
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Font
import java.text.DecimalFormat
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * Retained logged-in UI. Labels, combo box, and buttons are built once and
 * mutated in [update] — no component rebuilding.
 */
internal class LoggedInProfileUi(
    private val dashboard: () -> Unit,
    private val logout: () -> Unit,
    private val organization: (String?) -> Unit,
) : JPanel(BorderLayout()) {

    private val nameLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
    private val emailLabel = JBLabel().apply { foreground = UiStyle.Colors.weak() }

    private val balanceLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
    private val balanceContainer = panel {
        row {
            label(KiloBundle.message("profile.balance.title")).gap(RightGap.SMALL)
            cell(balanceLabel)
        }
    }

    private val comboModel = DefaultComboBoxModel<String>()
    val combo = JComboBox(comboModel)
    private val orgContainer = panel {
        group(KiloBundle.message("profile.group.organization")) {
            row(KiloBundle.message("profile.label.account")) {
                cell(combo).align(AlignX.FILL)
            }
        }
    }

    val dashboardBtn = JButton(KiloBundle.message("profile.action.dashboard"))
        .also { it.addActionListener { dashboard() } }
    val logoutBtn = JButton(KiloBundle.message("profile.action.logout"))
        .also { it.addActionListener { logout() } }

    private val content = panel {
        group(KiloBundle.message("profile.group.account")) {
            row { cell(nameLabel) }
            row { cell(emailLabel) }
        }
        row { cell(balanceContainer) }.topGap(TopGap.SMALL)
        row { cell(orgContainer) }
        row {
            cell(dashboardBtn).gap(RightGap.SMALL)
            cell(logoutBtn)
        }.bottomGap(BottomGap.SMALL)
    }

    private var applying = false
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

    fun update(profile: ProfileDto) {
        currentProf = profile

        val display = profile.name?.takeIf { it.isNotBlank() } ?: profile.email
        if (nameLabel.text != display) nameLabel.text = display

        val showEmail = profile.name != null
        emailLabel.isVisible = showEmail
        if (showEmail && emailLabel.text != profile.email) emailLabel.text = profile.email

        val bal = profile.balance
        if (bal != null) {
            val fmt = DecimalFormat("$#,##0.00")
            val balText = fmt.format(bal.balance)
            if (balanceLabel.text != balText) balanceLabel.text = balText
            balanceContainer.isVisible = true
        } else {
            balanceContainer.isVisible = false
        }

        applyOrganizations(profile)
    }

    private fun applyOrganizations(profile: ProfileDto) {
        val orgs = profile.organizations
        val options = listOf(KiloBundle.message("profile.personalAccount")) +
                orgs.map { "${it.name} (${it.role.lowercase()})" }

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
        if (orgContainer.isVisible != show) {
            orgContainer.isVisible = show
            revalidate()
            repaint()
        }
    }
}
