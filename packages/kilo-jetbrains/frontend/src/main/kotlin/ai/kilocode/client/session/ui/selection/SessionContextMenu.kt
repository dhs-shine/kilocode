package ai.kilocode.client.session.ui.selection

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal object SessionContextMenu {
    private val KEY = Any()
    private const val ID = "Kilo.Session.ContextMenu"

    @RequiresEdt
    fun install(root: JComponent, parent: Disposable) {
        if (root.getClientProperty(KEY) == true) return
        val listener = AWTEventListener { event ->
            val mouse = event as? MouseEvent ?: return@AWTEventListener
            if (!mouse.isPopupTrigger) return@AWTEventListener
            if (mouse.id != MouseEvent.MOUSE_PRESSED && mouse.id != MouseEvent.MOUSE_RELEASED) return@AWTEventListener
            show(root, mouse)
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
        root.putClientProperty(KEY, true)
        Disposer.register(parent) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            root.putClientProperty(KEY, null)
        }
    }

    @RequiresEdt
    internal fun target(root: JComponent, src: Component, point: Point): Component? {
        if (!inside(root, src)) return null
        val pt = SwingUtilities.convertPoint(src, point, root)
        if (!root.contains(pt)) return null
        val deep = SwingUtilities.getDeepestComponentAt(root, pt.x, pt.y)?.takeIf { inside(root, it) } ?: src
        return provider(root, deep) ?: deep
    }

    @RequiresEdt
    private fun show(root: JComponent, event: MouseEvent) {
        if (!root.isShowing) return
        val src = event.component ?: return
        val target = target(root, src, event.point) ?: return
        val group = ActionManager.getInstance().getAction(ID) as? ActionGroup ?: return
        val ctx = DataManager.getInstance().getDataContext(target)
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            null,
            group,
            ctx,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true,
        )
        val point = SwingUtilities.convertPoint(src, event.point, target)
        popup.show(RelativePoint(target, point))
        event.consume()
    }

    private fun provider(root: JComponent, comp: Component): Component? {
        var current: Component? = comp
        while (current != null && inside(root, current)) {
            if (current is UiDataProvider) return current
            current = current.parent
        }
        return null
    }

    private fun inside(root: JComponent, comp: Component): Boolean = comp === root || SwingUtilities.isDescendingFrom(comp, root)
}
