package ai.kilocode.client.settings.agents

import ai.kilocode.cli.KiloCliParser
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.BaseContentPanel
import ai.kilocode.client.settings.base.SettingsRow
import ai.kilocode.client.settings.base.SettingsToggle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AgentEditDialog(private val agent: AgentEditDraft) : DialogWrapper(true) {
    private val description = JBTextArea(agent.description.orEmpty()).apply {
        rows = 3
        lineWrap = true
        wrapStyleWord = true
    }
    private val prompt = JBTextArea(agent.prompt.orEmpty()).apply {
        rows = 8
        lineWrap = true
        wrapStyleWord = true
    }
    private val model = JBTextField(agent.model.orEmpty())
    private val variant = JBTextField(agent.variant.orEmpty())
    private val mode = ComboBox(arrayOf(KiloCliParser.MODE_PRIMARY, KiloCliParser.MODE_SUBAGENT, KiloCliParser.MODE_ALL)).apply {
        selectedItem = agent.mode
    }
    private val temperature = JBTextField(agent.temperature?.toString().orEmpty())
    private val top = JBTextField(agent.topP?.toString().orEmpty())
    private val steps = JBTextField(agent.steps?.toString().orEmpty())
    private var hidden = agent.hidden
    private var disabled = agent.disable

    init {
        title = KiloBundle.message("settings.agentBehavior.agents.edit.title", agent.displayName ?: agent.name)
        init()
        initValidation()
    }

    fun result(): AgentEditDraft = agent.copy(
        description = text(description.text),
        prompt = text(prompt.text),
        model = text(model.text),
        variant = text(variant.text),
        mode = mode.selectedItem?.toString() ?: agent.mode,
        hidden = hidden,
        disable = disabled,
        temperature = number(temperature.text),
        topP = number(top.text),
        steps = integer(steps.text),
    )

    override fun createCenterPanel(): JComponent {
        val panel = BaseContentPanel().apply {
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
        }
        panel.section(KiloBundle.message("settings.agentBehavior.agents.edit.identity")).apply {
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.name"),
                agent.name,
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.mode"),
                KiloBundle.message("settings.agentBehavior.agents.edit.mode.description"),
                mode,
            ))
        }
        panel.section(KiloBundle.message("settings.agentBehavior.agents.edit.instructions")).apply {
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.description"),
                KiloBundle.message("settings.agentBehavior.agents.edit.description.description"),
                scroll(description),
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.prompt"),
                KiloBundle.message("settings.agentBehavior.agents.edit.prompt.description"),
                scroll(prompt),
            ))
        }
        panel.section(KiloBundle.message("settings.agentBehavior.agents.edit.model")).apply {
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.model.override"),
                KiloBundle.message("settings.agentBehavior.agents.edit.model.override.description"),
                model,
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.variant"),
                KiloBundle.message("settings.agentBehavior.agents.edit.variant.description"),
                variant,
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.temperature"),
                KiloBundle.message("settings.agentBehavior.agents.edit.temperature.description"),
                temperature,
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.topP"),
                KiloBundle.message("settings.agentBehavior.agents.edit.topP.description"),
                top,
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.steps"),
                KiloBundle.message("settings.agentBehavior.agents.edit.steps.description"),
                steps,
            ))
        }
        panel.section(KiloBundle.message("settings.agentBehavior.agents.edit.visibility")).apply {
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.hidden"),
                KiloBundle.message("settings.agentBehavior.agents.edit.hidden.description"),
                SettingsToggle(hidden) { hidden = it },
            ))
            row(SettingsRow(
                KiloBundle.message("settings.agentBehavior.agents.edit.disabled"),
                KiloBundle.message("settings.agentBehavior.agents.edit.disabled.description"),
                SettingsToggle(disabled) { disabled = it },
            ))
        }
        return JPanel(BorderLayout()).apply {
            add(Stack.vertical().next(panel), BorderLayout.CENTER)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = description

    override fun getDimensionServiceKey(): String = "Kilo.AgentEditDialog"

    override fun doValidateAll(): List<ValidationInfo> = listOfNotNull(
        validateMode(),
        validateNumber(temperature, "settings.agentBehavior.agents.edit.temperature.invalid", min = 0.0),
        validateNumber(top, "settings.agentBehavior.agents.edit.topP.invalid", min = 0.0, max = 1.0),
        validateSteps(),
    )

    private fun validateMode(): ValidationInfo? {
        val value = mode.selectedItem?.toString()
        if (value == KiloCliParser.MODE_PRIMARY || value == KiloCliParser.MODE_SUBAGENT || value == KiloCliParser.MODE_ALL) return null
        return ValidationInfo(KiloBundle.message("settings.agentBehavior.agents.edit.mode.invalid"), mode)
    }

    private fun validateNumber(field: JBTextField, key: String, min: Double? = null, max: Double? = null): ValidationInfo? {
        val value = field.text.trim()
        if (value.isBlank()) return null
        val parsed = value.toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) return ValidationInfo(KiloBundle.message(key), field)
        if (min != null && parsed < min) return ValidationInfo(KiloBundle.message(key), field)
        if (max != null && parsed > max) return ValidationInfo(KiloBundle.message(key), field)
        return null
    }

    private fun validateSteps(): ValidationInfo? {
        val value = steps.text.trim()
        if (value.isBlank()) return null
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed > 0) return null
        return ValidationInfo(KiloBundle.message("settings.agentBehavior.agents.edit.steps.invalid"), steps)
    }

    private fun scroll(area: JBTextArea) = JBScrollPane(area)

    private fun text(value: String): String? = value.trim().takeIf { it.isNotBlank() }

    private fun number(value: String): Double? = text(value)?.toDoubleOrNull()

    private fun integer(value: String): Long? = text(value)?.toLongOrNull()
}
