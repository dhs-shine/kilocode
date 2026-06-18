package ai.kilocode.client.settings.providers

import ai.kilocode.client.settings.base.SettingsListItem
import ai.kilocode.client.settings.base.SettingsListRenderer
import ai.kilocode.client.settings.base.settingsListCellAt
import ai.kilocode.client.settings.base.settingsListCellBounds
import ai.kilocode.client.settings.base.settingsListVisibleCells
import com.intellij.ui.CollectionListModel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class ProviderListRenderer(
    private val rows: CollectionListModel<ProviderListRow>,
) : JPanel(BorderLayout()), ListCellRenderer<ProviderListRow> {
    companion object {
        fun actionAt(list: JList<*>, bounds: Rectangle, point: Point, row: ProviderListRow, selected: Boolean): ProviderListAction? {
            val id = settingsListCellAt(list, bounds, point, row, selected) ?: return null
            return ProviderListAction.entries.firstOrNull { it.name == id }
        }

        internal fun actionBounds(list: JList<*>, bounds: Rectangle, row: ProviderListRow, selected: Boolean): Map<ProviderListAction, Rectangle> {
            val cells = settingsListCellBounds(list, bounds, row, selected)
            return cells.mapNotNull { (id, rect) -> ProviderListAction.entries.firstOrNull { it.name == id }?.let { it to rect } }.toMap()
        }

        internal fun text(action: ProviderListAction) = providerListActionText(action)

        internal fun visibleActions(row: ProviderListRow, selected: Boolean): List<ProviderListAction> {
            return settingsListVisibleCells(row, selected).mapNotNull { cell -> ProviderListAction.entries.firstOrNull { it.name == cell.id } }
        }
    }

    private val model = CollectionListModel<SettingsListItem>()
    private val renderer = SettingsListRenderer(model)

    override fun getListCellRendererComponent(
        list: JList<out ProviderListRow>,
        value: ProviderListRow,
        index: Int,
        selected: Boolean,
        focused: Boolean,
    ): JPanel {
        model.replaceAll(rows.items)
        val component = renderer.getListCellRendererComponent(list as JList<out SettingsListItem>, value, index, selected, focused)
        removeAll()
        add(component, BorderLayout.CENTER)
        return this
    }

    internal fun actionTexts() = renderer.cellTexts()

    internal fun descriptionText() = renderer.descriptionText()

    internal fun providerIconVisible() = renderer.iconVisible()

    internal fun providerIconSize(): Dimension? = renderer.iconSize()
}
