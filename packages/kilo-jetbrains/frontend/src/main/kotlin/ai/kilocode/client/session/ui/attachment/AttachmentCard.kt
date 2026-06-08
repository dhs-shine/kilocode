package ai.kilocode.client.session.ui.attachment

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.HAlign
import ai.kilocode.client.ui.layout.VAlign
import ai.kilocode.client.ui.layout.align
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.LayoutManager2
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

data class AttachmentCardItem(
    val name: String,
    val mime: String,
    val url: String,
    val path: Path? = null,
)

open class AttachmentCard(
    private val item: AttachmentCardItem,
    remove: (() -> Unit)? = null,
    open: (() -> Unit)? = null,
) : JPanel(CardLayout()) {
    private var gen = 0
    private var loaded = false
    private val icon = mimeIcon(item.mime)
    private val preview = PreviewPanel().apply {
        layout = BorderLayout()
        add(JBLabel(icon, SwingConstants.CENTER).align(HAlign.CENTER, VAlign.CENTER), BorderLayout.CENTER)
    }
    private var title: JBLabel? = null
    private var kind: JBLabel? = null
    private val titleLabel = JBLabel(item.name).apply {
        font = JBFont.small().asBold()
        title = this
    }
    private val kindLabel = JBLabel(item.mime).apply {
        font = JBFont.small()
        foreground = UIUtil.getContextHelpForeground()
        kind = this
    }
    private val content = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(UiStyle.Gap.sm())
        add(preview, BorderLayout.NORTH)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(UiStyle.Gap.sm())
                add(titleLabel, BorderLayout.NORTH)
                add(kindLabel, BorderLayout.SOUTH)
            },
            BorderLayout.CENTER,
        )
    }
    private val action = remove?.let { callback ->
        HoverIcon().apply {
            icon = AllIcons.Actions.Close
            toolTipText = KiloBundle.message("prompt.attachment.remove", item.name)
            accessibleContext?.accessibleName = toolTipText
            addActionListener { callback() }
        }
    }

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = KiloBundle.message("prompt.attachment.tooltip", item.name, item.mime, item.url)
        accessibleContext?.accessibleName = KiloBundle.message("prompt.attachment.open", item.name)
        add(content)
        if (action != null) add(action)
        if (open != null) installOpen(content, open)
    }

    override fun getPreferredSize(): Dimension = JBUI.size(
        SessionUiStyle.View.Attachment.CARD_WIDTH,
        SessionUiStyle.View.Attachment.CARD_HEIGHT,
    )

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize

    override fun addNotify() {
        super.addNotify()
        if (loaded) return
        loaded = true
        load()
    }

    override fun updateUI() {
        super.updateUI()
        title?.font = JBFont.small().asBold()
        kind?.font = JBFont.small()
        kind?.foreground = UIUtil.getContextHelpForeground()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(SessionUiStyle.View.Attachment.CORNER_ARC)
            g2.color = SessionUiStyle.View.surface()
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            g2.color = SessionUiStyle.View.line()
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    @RequiresEdt
    private fun load() {
        val path = local(item) ?: return
        if (!item.mime.startsWith("image/")) return
        val stamp = ++gen
        val size = JBUI.size(
            SessionUiStyle.View.Attachment.CARD_WIDTH - UiStyle.Gap.lg() * 2,
            SessionUiStyle.View.Attachment.PREVIEW_HEIGHT,
        )
        ApplicationManager.getApplication().executeOnPooledThread {
            val image = runCatching { ImageIO.read(path.toFile()) }.getOrNull()
            val scaled = image?.let { scale(it, size.width, size.height) }
            if (scaled == null) return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (gen != stamp || !isDisplayable) return@invokeLater
                preview.setIcon(ImageIcon(scaled))
            }
        }
    }

    private fun installOpen(root: Component, open: () -> Unit) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                open()
            }
        }
        fun visit(node: Component) {
            if (node is JButton) return
            node.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            node.addMouseListener(listener)
            if (node is Container) node.components.forEach(::visit)
        }
        visit(root)
    }

    private class PreviewPanel : JPanel(BorderLayout()) {
        init {
            isOpaque = false
        }

        override fun getPreferredSize(): Dimension = JBUI.size(
            SessionUiStyle.View.Attachment.CARD_WIDTH - UiStyle.Gap.lg() * 2,
            SessionUiStyle.View.Attachment.PREVIEW_HEIGHT,
        )

        fun setIcon(next: Icon) {
            removeAll()
            add(JBLabel(next, SwingConstants.CENTER).align(HAlign.CENTER, VAlign.CENTER), BorderLayout.CENTER)
            revalidate()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(SessionUiStyle.View.Attachment.CORNER_ARC)
                g2.color = SessionUiStyle.View.headerHover()
                g2.fillRoundRect(0, 0, width, height, arc, arc)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private class CardLayout : LayoutManager2 {
        override fun addLayoutComponent(comp: Component, constraints: Any?) = Unit
        override fun addLayoutComponent(name: String?, comp: Component) = Unit
        override fun removeLayoutComponent(comp: Component) = Unit
        override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent)
        override fun preferredLayoutSize(parent: Container) = JBUI.size(
            SessionUiStyle.View.Attachment.CARD_WIDTH,
            SessionUiStyle.View.Attachment.CARD_HEIGHT,
        )

        override fun maximumLayoutSize(target: Container) = preferredLayoutSize(target)
        override fun getLayoutAlignmentX(target: Container) = 0f
        override fun getLayoutAlignmentY(target: Container) = 0f
        override fun invalidateLayout(target: Container) = Unit

        override fun layoutContainer(parent: Container) {
            if (parent.componentCount == 0) return
            val content = parent.getComponent(0)
            content.setBounds(0, 0, parent.width, parent.height)
            if (parent.componentCount < 2) return
            val action = parent.getComponent(1)
            val size = JBUI.scale(SessionUiStyle.View.Attachment.CLOSE_SIZE)
            action.setBounds(parent.width - size - UiStyle.Gap.xs(), UiStyle.Gap.xs(), size, size)
        }
    }
}

private fun scale(image: Image, width: Int, height: Int): Image {
    val iw = image.getWidth(null)
    val ih = image.getHeight(null)
    if (iw <= 0 || ih <= 0) return image
    val ratio = minOf(width.toDouble() / iw, height.toDouble() / ih)
    val w = maxOf(1, (iw * ratio).toInt())
    val h = maxOf(1, (ih * ratio).toInt())
    return image.getScaledInstance(w, h, Image.SCALE_SMOOTH)
}

private fun local(item: AttachmentCardItem): Path? {
    if (item.path != null) return item.path
    val uri = runCatching { URI.create(item.url) }.getOrNull() ?: return null
    if (uri.scheme != "file") return null
    return runCatching { Path.of(uri) }.getOrNull()
}

private fun mimeIcon(mime: String): Icon = when {
    mime.startsWith("image/") -> AllIcons.FileTypes.Image
    mime == "application/x-directory" -> AllIcons.Nodes.Folder
    else -> AllIcons.FileTypes.Text
}
