package ai.kilocode.client.settings.models

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.layout.StackAxis
import com.intellij.ui.components.ActionLink

internal class ModelSettingPicker(
    private val clear: () -> Unit,
) : Stack(StackAxis.HORIZONTAL, UiStyle.Gap.md()) {
    val picker = ModelPicker()
    private val reset = ActionLink(KiloBundle.message("settings.models.reset")) { clear() }
    private var active = true

    init {
        picker.allowEmpty = true
        picker.emptyText = KiloBundle.message("settings.models.notSet")
        next(picker)
        next(reset)
        reset.isVisible = false
    }

    fun setItems(items: List<ModelPicker.Item>, selected: String?) {
        picker.setItems(items, selected)
        if (!active) picker.isEnabled = false
    }

    fun setClearVisible(value: Boolean) {
        reset.isVisible = value
    }

    override fun setEnabled(value: Boolean) {
        active = value
        super.setEnabled(value)
        if (!value) picker.isEnabled = false
        reset.isEnabled = value
    }
}
