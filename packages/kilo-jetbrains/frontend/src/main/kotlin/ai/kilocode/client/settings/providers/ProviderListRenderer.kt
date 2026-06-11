package ai.kilocode.client.settings.providers

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.PickerRow
import ai.kilocode.client.ui.UiStyle
import com.intellij.ui.CollectionListModel
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.UIManager

private const val ACTION_GAP = 8

internal class ProviderListRenderer(
    private val model: CollectionListModel<ProviderListRow>,
) : JPanel(BorderLayout()), ListCellRenderer<ProviderListRow> {
    companion object {
        fun actionAt(list: JList<*>, bounds: Rectangle, point: Point, row: ProviderListRow): ProviderListAction? {
            val height = buttonHeight(list)
            val top = bounds.y + (bounds.height - height) / 2
            if (point.y !in top..(top + height)) return null
            var edge = bounds.x + bounds.width - UiStyle.Gap.pad()
            for (action in row.actions.asReversed()) {
                val width = buttonWidth(list, action)
                val left = edge - width
                if (point.x in left..edge) return action.takeIf(row::enabled)
                edge = left - JBUI.scale(ACTION_GAP)
            }
            return null
        }

        internal fun actionBounds(list: JList<*>, bounds: Rectangle, row: ProviderListRow): Map<ProviderListAction, Rectangle> {
            val height = buttonHeight(list)
            val top = bounds.y + (bounds.height - height) / 2
            var edge = bounds.x + bounds.width - UiStyle.Gap.pad()
            val out = linkedMapOf<ProviderListAction, Rectangle>()
            for (action in row.actions.asReversed()) {
                val width = buttonWidth(list, action)
                val left = edge - width
                out[action] = Rectangle(left, top, width, height)
                edge = left - JBUI.scale(ACTION_GAP)
            }
            return out
        }

        private fun buttonWidth(list: JList<*>, action: ProviderListAction): Int {
            val text = text(action)
            val metrics = list.getFontMetrics(list.font)
            return metrics.stringWidth(text) + UiStyle.Gap.pad() * 2
        }

        private fun buttonHeight(list: JList<*>): Int {
            val metrics = list.getFontMetrics(list.font)
            return metrics.height + UiStyle.Gap.sm() * 2
        }

        internal fun text(action: ProviderListAction) = when (action) {
            ProviderListAction.CONNECT -> KiloBundle.message("settings.providers.connect")
            ProviderListAction.OAUTH -> KiloBundle.message("settings.providers.oauth")
            ProviderListAction.DISCONNECT -> KiloBundle.message("settings.providers.disconnect")
            ProviderListAction.ENABLE -> KiloBundle.message("settings.providers.enable")
        }
    }

    private val sep = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
    private val top = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty()
        add(sep, BorderLayout.NORTH)
    }
    private val title = SimpleColoredComponent()
    private val desc = JBLabel()
    private val text = JPanel(BorderLayout()).apply {
        add(title, BorderLayout.NORTH)
        add(desc, BorderLayout.SOUTH)
    }
    private val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(ACTION_GAP), 0))
    private val row = JPanel(BorderLayout()).apply {
        add(text, BorderLayout.CENTER)
        add(actions, BorderLayout.EAST)
    }
    private val wrap = PickerRow()

    init {
        isOpaque = true
        top.isOpaque = true
        UiStyle.Components.transparent(row, title, text, desc, actions)
        row.border = JBUI.Borders.empty(
            UiStyle.Gap.md(),
            UiStyle.Gap.lg(),
            UiStyle.Gap.md(),
            UiStyle.Gap.pad(),
        )
        wrap.setContent(row)
        add(top, BorderLayout.NORTH)
        add(wrap, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out ProviderListRow>,
        value: ProviderListRow,
        index: Int,
        selected: Boolean,
        focused: Boolean,
    ): JPanel {
        val focus = selected || list.hasFocus() || focused
        val fg = UIUtil.getListForeground(selected, focus)
        val weak = if (selected) fg else UiStyle.Colors.weak()
        val current = model.items.getOrNull(index)
        val section = if (current === value) providerListSectionTitle(model.items, index) else null

        background = list.background
        top.background = list.background
        wrap.update(list, selected, focus)
        sep.caption = section
        sep.setHideLine(index == 0)
        top.isVisible = section != null

        title.clear()
        title.append(value.provider.name, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, fg))
        desc.text = providerDescription(value.provider)
        desc.foreground = weak

        actions.removeAll()
        for (action in value.actions) {
            actions.add(ActionLabel(action).apply {
                isEnabled = value.enabled(action)
                foreground = if (isEnabled) UIManager.getColor("Button.foreground") ?: UIUtil.getLabelForeground()
                    else UIManager.getColor("Button.disabledText") ?: UIUtil.getContextHelpForeground()
                background = UIManager.getColor("Button.background")
            })
        }
        top.invalidate()
        return this
    }

    internal fun actionTexts() = actions.components.filterIsInstance<JBLabel>().map { it.text }

    private class ActionLabel(action: ProviderListAction) : JBLabel(text(action)) {
        init {
            horizontalAlignment = SwingConstants.CENTER
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor()),
                JBUI.Borders.empty(UiStyle.Gap.sm(), UiStyle.Gap.pad()),
            )
            isOpaque = true
        }
    }
}
