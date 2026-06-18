package ai.kilocode.client.settings.base

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent

class SettingsListViewTest : BasePlatformTestCase() {
    fun `test list owns formatted description tooltip`() {
        edt {
            val view = SettingsListView("Empty") { _, _ -> }
            val row = item("with", "Alpha", "Use <safe> text\nAcross lines")
            view.update(listOf(row, item("without", "Beta", null)))
            view.list.size = Dimension(320, 120)
            view.list.doLayout()
            UIUtil.dispatchAllInvocationEvents()

            val bounds = view.list.getCellBounds(0, 0)
            val tip = view.list.getToolTipText(event(view.list, Point(bounds.x + 4, bounds.y + 4)))

            assertNotNull(tip)
            assertTrue(tip, tip!!.startsWith("<html>"))
            assertTrue(tip, tip.contains("Use &lt;safe&gt; text"))
            assertTrue(tip, tip.contains("<br>Across lines"))
        }
    }

    fun `test list description tooltip ignores blank rows and outside points`() {
        edt {
            val view = SettingsListView("Empty") { _, _ -> }
            view.update(listOf(item("without", "Beta", null)))
            view.list.size = Dimension(320, 80)
            view.list.doLayout()
            UIUtil.dispatchAllInvocationEvents()

            val bounds = view.list.getCellBounds(0, 0)

            assertNull(view.list.getToolTipText(event(view.list, Point(bounds.x + 4, bounds.y + 4))))
            assertNull(view.list.getToolTipText(event(view.list, Point(4, bounds.y + bounds.height + 20))))
        }
    }

    private fun item(id: String, name: String, note: String?) = object : SettingsListItem {
        override val key = id
        override val title = name
        override val description = note
    }

    private fun event(list: javax.swing.JList<*>, point: Point) = MouseEvent(
        list,
        MouseEvent.MOUSE_MOVED,
        System.currentTimeMillis(),
        0,
        point.x,
        point.y,
        0,
        false,
    )

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
