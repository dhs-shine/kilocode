package ai.kilocode.client.settings.base

import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JList

private const val CELL_GAP = 8

internal data class SettingsBadge(val text: String, val style: UiStyle.Badge.Style = UiStyle.Badge.Secondary)

internal data class SettingsListCell(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val alwaysVisible: Boolean = false,
    val icon: Icon? = null,
    val iconOnly: Boolean = false,
)

internal interface SettingsListItem {
    val key: String
    val title: String
    val description: String? get() = null
    val icon: Icon? get() = null
    val section: String? get() = null
    val badges: List<SettingsBadge> get() = emptyList()
    val cells: List<SettingsListCell> get() = emptyList()
    val disabled: Boolean get() = false
}

internal fun settingsListSectionTitle(items: List<SettingsListItem>, index: Int): String? {
    val item = items.getOrNull(index) ?: return null
    val prev = items.getOrNull(index - 1)
    return if (prev?.section != item.section) item.section else null
}

internal fun settingsListVisibleCells(item: SettingsListItem, selected: Boolean): List<SettingsListCell> {
    if (item.disabled) return emptyList()
    return item.cells.filter { selected || it.alwaysVisible }
}

internal fun settingsListCellAt(
    list: JList<*>,
    bounds: Rectangle,
    point: Point,
    item: SettingsListItem,
    selected: Boolean,
): String? {
    val cells = settingsListCellBounds(list, bounds, item, selected)
    return settingsListVisibleCells(item, selected)
        .firstOrNull { cell -> cell.enabled && cells[cell.id]?.contains(point) == true }
        ?.id
}

internal fun settingsListCellBounds(
    list: JList<*>,
    bounds: Rectangle,
    item: SettingsListItem,
    selected: Boolean,
): Map<String, Rectangle> {
    val height = settingsListCellHeight(list)
    val top = bounds.y + (bounds.height - height) / 2
    var edge = bounds.x + bounds.width - UiStyle.Gap.pad()
    val out = linkedMapOf<String, Rectangle>()
    for (cell in settingsListVisibleCells(item, selected).asReversed()) {
        val width = settingsListCellWidth(list, cell)
        val left = edge - width
        out[cell.id] = Rectangle(left, top, width, height)
        edge = left - JBUI.scale(CELL_GAP)
    }
    return out
}

private fun settingsListCellWidth(list: JList<*>, cell: SettingsListCell): Int {
    val metrics = list.getFontMetrics(list.font)
    val icon = cell.icon?.iconWidth ?: 0
    val gap = if (icon > 0 && !cell.iconOnly && cell.label.isNotBlank()) JBUI.CurrentTheme.ActionsList.elementIconGap() else 0
    val text = if (cell.iconOnly) 0 else metrics.stringWidth(cell.label)
    return icon + gap + text + UiStyle.Gap.pad() * 2
}

private fun settingsListCellHeight(list: JList<*>): Int {
    val metrics = list.getFontMetrics(list.font)
    return metrics.height + UiStyle.Gap.sm() * 2
}

internal fun settingsListCellGap() = JBUI.scale(CELL_GAP)
