package ai.kilocode.client.session.ui.selection

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.HoverIcon
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class SessionHoverCopyOverlay(
    private val root: JComponent,
    parent: Disposable,
) : JPanel(null), Disposable {
    private var target: SessionCopyTarget? = null
    private var balloon: Balloon? = null
    private val button = HoverIcon(fill = true).apply {
        icon = AllIcons.Actions.Copy
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = KiloBundle.message("session.copy.hover")
    }

    init {
        isVisible = false
        isOpaque = false
        add(button)
        button.addActionListener { copy() }
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                dismiss()
            }
        })

        val listener = AWTEventListener { event ->
            val mouse = event as? MouseEvent ?: return@AWTEventListener
            when (mouse.id) {
                MouseEvent.MOUSE_MOVED,
                MouseEvent.MOUSE_DRAGGED,
                MouseEvent.MOUSE_EXITED -> sync(mouse)
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(
            listener,
            AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK,
        )
        Disposer.register(parent, this)
        Disposer.register(this) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }

    @RequiresEdt
    fun bounds(pane: JPanel, child: JComponent): Rectangle {
        val item = target ?: return Rectangle()
        val anchor = item.copyAnchor
        if (!anchor.isShowing && anchor.parent == null) return Rectangle()
        val visible = anchor.visibleRect
        if (visible.isEmpty) return Rectangle()
        val size = child.preferredSize
        val gap = JBUI.scale(4)
        val pt = SwingUtilities.convertPoint(anchor, Point(visible.x + visible.width, visible.y), pane)
        val x = (pt.x - size.width - gap).coerceIn(0, (pane.width - size.width).coerceAtLeast(0))
        val y = (pt.y + gap).coerceIn(0, (pane.height - size.height).coerceAtLeast(0))
        return Rectangle(x, y, size.width, size.height)
    }

    override fun doLayout() {
        button.setBounds(0, 0, width, height)
    }

    override fun getPreferredSize() = button.preferredSize

    override fun getMinimumSize() = button.minimumSize

    override fun getMaximumSize() = button.maximumSize

    @RequiresEdt
    private fun sync(event: MouseEvent) {
        val src = event.component ?: return conceal()
        if (SessionTargetResolver.inside(this, src)) return retain()
        if (contains(target, src, event.point)) return
        val item = SessionTargetResolver.copy(root, src, event.point, this)
        if (item == null) {
            conceal()
            return
        }
        show(item)
    }

    @RequiresEdt
    private fun show(item: SessionCopyTarget) {
        if (target === item && isVisible) return
        target = item
        isVisible = true
        parent?.doLayout()
        revalidate()
        repaint()
    }

    @RequiresEdt
    private fun retain() {
        if (target == null || isVisible) return
        isVisible = true
    }

    @RequiresEdt
    internal fun contains(item: SessionCopyTarget?, src: Component, point: Point): Boolean {
        val anchor = item?.copyAnchor ?: return false
        if (!SessionTargetResolver.inside(anchor, src)) return false
        val pt = SwingUtilities.convertPoint(src, point, anchor)
        return anchor.contains(pt)
    }

    @RequiresEdt
    fun clear() {
        conceal()
    }

    @RequiresEdt
    private fun dismiss() {
        balloon?.hide()
        balloon = null
    }

    @RequiresEdt
    private fun conceal() {
        dismiss()
        if (target == null && !isVisible) return
        target = null
        isVisible = false
        revalidate()
        repaint()
    }

    @RequiresEdt
    private fun copy() {
        val text = target?.copyText()?.takeIf { it.isNotEmpty() } ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        dismiss()
        balloon = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(KiloBundle.message("session.copy.copied"), null, null, null)
            .createBalloon()
            .also { item ->
                item.setAnimationEnabled(false)
                item.show(RelativePoint(button, Point(button.width / 2, 0)), Balloon.Position.above)
            }
    }

    override fun dispose() = Unit
}
