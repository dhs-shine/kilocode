package ai.kilocode.client.ui.md

import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.log.KiloLog
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Block
import org.commonmark.node.Document
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.Color
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.StyleSheet

@Suppress("UnstableApiUsage")
internal class MdViewHybrid(
    style: SessionStyle = SessionStyle.current(),
) : MdView {
    companion object {
        private val LOG = KiloLog.create(MdViewHybrid::class.java)
    }

    private val listeners = mutableListOf<MdView.LinkListener>()
    private val source = StringBuilder()
    private var style = style
    private var rendered = ""

    private val extensions = listOf(
        AutolinkExtension.create(),
        TablesExtension.create(),
        StrikethroughExtension.create(),
    )

    private val parser: Parser = Parser.builder().extensions(extensions).build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    private var fontOverride: Font? = null
    private var foregroundOverride: Color? = null
    private var backgroundOverride: Color? = null
    private var linkColorOverride: Color? = null
    private var codeBgOverride: Color? = null
    private var preBgOverride: Color? = null
    private var preFgOverride: Color? = null
    private var codeFontOverride: String? = null
    private var quoteBorderOverride: Color? = null
    private var quoteFgOverride: Color? = null
    private var tableBorderOverride: Color? = null
    private var opaqueState = true

    private val root = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = true
        background = opts().background
    }

    override val component: JComponent get() = root

    override var font: Font
        get() = fontOverride ?: opts().font
        set(value) {
            if (fontOverride == value) return
            fontOverride = value
            syncStyle()
        }

    override var foreground: Color
        get() = foregroundOverride ?: opts().foreground
        set(value) {
            if (foregroundOverride == value) return
            foregroundOverride = value
            syncStyle()
        }

    override var background: Color
        get() = backgroundOverride ?: opts().background
        set(value) {
            if (backgroundOverride == value) return
            backgroundOverride = value
            syncStyle()
        }

    override var linkColor: Color
        get() = linkColorOverride ?: opts().linkColor
        set(value) {
            if (linkColorOverride == value) return
            linkColorOverride = value
            syncStyle()
        }

    override var codeBg: Color
        get() = codeBgOverride ?: opts().codeBg
        set(value) {
            if (codeBgOverride == value) return
            codeBgOverride = value
            syncStyle()
        }

    override var preBg: Color
        get() = preBgOverride ?: opts().preBg
        set(value) {
            if (preBgOverride == value) return
            preBgOverride = value
            syncStyle()
        }

    override var preFg: Color
        get() = preFgOverride ?: opts().preFg
        set(value) {
            if (preFgOverride == value) return
            preFgOverride = value
            syncStyle()
        }

    override var codeFont: String
        get() = codeFontOverride ?: opts().codeFont
        set(value) {
            if (codeFontOverride == value) return
            codeFontOverride = value
            syncStyle()
        }

    override var quoteBorder: Color
        get() = quoteBorderOverride ?: opts().quoteBorder
        set(value) {
            if (quoteBorderOverride == value) return
            quoteBorderOverride = value
            syncStyle()
        }

    override var quoteFg: Color
        get() = quoteFgOverride ?: opts().quoteFg
        set(value) {
            if (quoteFgOverride == value) return
            quoteFgOverride = value
            syncStyle()
        }

    override var tableBorder: Color
        get() = tableBorderOverride ?: opts().tableBorder
        set(value) {
            if (tableBorderOverride == value) return
            tableBorderOverride = value
            syncStyle()
        }

    override var opaque: Boolean
        get() = opaqueState
        set(value) {
            if (opaqueState == value) return
            opaqueState = value
            syncStyle()
        }

    override fun applyStyle(style: SessionStyle) {
        if (this.style == style) return
        this.style = style
        syncStyle()
    }

    override fun resetStyles() {
        fontOverride = null
        foregroundOverride = null
        backgroundOverride = null
        linkColorOverride = null
        codeBgOverride = null
        preBgOverride = null
        preFgOverride = null
        codeFontOverride = null
        quoteBorderOverride = null
        quoteFgOverride = null
        tableBorderOverride = null
        opaqueState = true
        syncStyle()
    }

    override fun set(text: String) {
        if (source.toString() == text) return
        source.clear()
        source.append(text)
        syncBlocks()
    }

    override fun append(delta: String) {
        if (delta.isEmpty()) return
        source.append(delta)
        syncBlocks()
    }

    override fun clear() {
        if (source.isEmpty() && rendered.isEmpty() && root.componentCount == 0) return
        source.clear()
        rendered = ""
        root.removeAll()
        root.revalidate()
        root.repaint()
    }

    override fun addLinkListener(listener: MdView.LinkListener) {
        listeners.add(listener)
    }

    override fun removeLinkListener(listener: MdView.LinkListener) {
        listeners.remove(listener)
    }

    override fun markdown(): String = source.toString()

    override fun html(): String = rendered

    override fun overrideSheet(): String = MdCommon.rules(opts())

    override fun simulateLink(href: String) {
        dispatch(MdView.LinkEvent(href))
    }

    private fun syncStyle() {
        val opts = opts()
        root.isOpaque = opts.opaque
        if (opts.opaque) root.background = opts.background
        syncBlocks()
    }

    private fun syncBlocks() {
        val text = source.toString()
        val doc = parser.parse(text)
        val body = renderer.render(doc)
        rendered = body
        root.removeAll()
        if (text.isEmpty()) {
            root.revalidate()
            root.repaint()
            return
        }
        val visitor = Visitor()
        doc.accept(visitor)
        root.revalidate()
        root.repaint()
    }

    private fun addGap() {
        if (root.componentCount == 0) return
        root.add(Box.createVerticalStrut(JBUI.scale(6)))
    }

    private fun addBlock(component: JComponent) {
        addGap()
        component.alignmentX = JComponent.LEFT_ALIGNMENT
        root.add(component)
    }

    private fun htmlBlock(node: Node): JComponent {
        val opts = opts()
        val body = renderer.render(node)
        return JBHtmlPane(
            JBHtmlPaneStyleConfiguration {
                enableInlineCodeBackground = true
                enableCodeBlocksBackground = true
            },
            JBHtmlPaneConfiguration {
                customStyleSheetProvider { sheet() }
            },
        ).apply {
            isEditable = false
            isOpaque = opts.opaque
            background = opts.background
            text = "<html><body>$body</body></html>"
            addHyperlinkListener { e ->
                if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return@addHyperlinkListener
                val href = e.description ?: return@addHyperlinkListener
                val pt = (e.inputEvent as? java.awt.event.MouseEvent)?.point
                dispatch(MdView.LinkEvent(href, pt))
            }
        }
    }

    private fun codeBlock(text: String, lang: String?): JComponent {
        val opts = opts()
        val field = runCatching { CodeField(file(lang), opts, text) }.getOrElse { err ->
            LOG.warn("kind=markdown codeEditor=true failed message=${err.message}", err)
            textArea(text, opts)
        }
        return JBScrollPane(field).apply {
            border = JBUI.Borders.empty()
            isOpaque = opts.opaque
            background = opts.preBg
            viewport.background = opts.preBg
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
    }

    private fun textArea(text: String, opts: MdStyle) = JBTextArea(text.trimEnd('\n')).apply {
        isEditable = false
        lineWrap = false
        isOpaque = opts.opaque
        background = opts.preBg
        foreground = opts.preFg
        font = Font(opts.codeFont, Font.PLAIN, style.editorSize)
        border = JBUI.Borders.empty(6, 8)
    }

    private inner class CodeField(file: FileType, opts: MdStyle, value: String) :
        com.intellij.ui.EditorTextField(ProjectManager.getInstance().defaultProject, file) {
        init {
            setFontInheritedFromLAF(false)
            font = Font(opts.codeFont, Font.PLAIN, style.editorSize)
            text = value.trimEnd('\n')
            isViewer = true
            addSettingsProvider { ed ->
                style.applyToEditor(ed)
                ed.setBorder(JBUI.Borders.empty())
                ed.scrollPane.border = JBUI.Borders.empty()
                ed.scrollPane.viewportBorder = JBUI.Borders.empty()
                ed.backgroundColor = opts.preBg
                ed.scrollPane.background = opts.preBg
                ed.scrollPane.viewport.background = opts.preBg
                ed.settings.isUseSoftWraps = false
                ed.settings.isAdditionalPageAtBottom = false
                ed.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            }
        }
    }

    private fun file(lang: String?): FileType {
        val key = lang?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
        val ext = when (key) {
            "kt", "kotlin" -> "kt"
            "js", "javascript" -> "js"
            "ts", "typescript" -> "ts"
            "tsx" -> "tsx"
            "java" -> "java"
            "py", "python" -> "py"
            "sh", "bash", "shell" -> "sh"
            "json" -> "json"
            "xml" -> "xml"
            "html" -> "html"
            "css" -> "css"
            "md", "markdown" -> "md"
            else -> ""
        }
        if (ext.isEmpty()) return PlainTextFileType.INSTANCE
        return FileTypeManager.getInstance().getFileTypeByExtension(ext)
    }

    private fun dispatch(event: MdView.LinkEvent) {
        for (l in listeners) l.onLink(event)
    }

    private fun sheet(): StyleSheet {
        val sheet = StyleSheet()
        val rules = overrideSheet()
        if (rules.isEmpty()) return sheet
        try {
            sheet.addRule(rules)
        } catch (err: Exception) {
            LOG.warn("kind=markdown css=true failed message=${err.message} rules=$rules", err)
        }
        return sheet
    }

    private fun opts(): MdStyle {
        val base = MdCommon.defaults(style)
        return base.copy(
            font = fontOverride ?: base.font,
            foreground = foregroundOverride ?: base.foreground,
            background = backgroundOverride ?: base.background,
            linkColor = linkColorOverride ?: base.linkColor,
            codeBg = codeBgOverride ?: base.codeBg,
            preBg = preBgOverride ?: base.preBg,
            preFg = preFgOverride ?: base.preFg,
            codeFont = codeFontOverride ?: base.codeFont,
            quoteBorder = quoteBorderOverride ?: base.quoteBorder,
            quoteFg = quoteFgOverride ?: base.quoteFg,
            tableBorder = tableBorderOverride ?: base.tableBorder,
            opaque = opaqueState,
        )
    }

    private inner class Visitor : AbstractVisitor() {
        override fun visit(document: Document) {
            visitChildren(document)
        }

        override fun visit(code: FencedCodeBlock) {
            addBlock(codeBlock(code.literal, code.info))
        }

        override fun visit(code: IndentedCodeBlock) {
            addBlock(codeBlock(code.literal, null))
        }

        public override fun visitChildren(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.next
                if (child is FencedCodeBlock || child is IndentedCodeBlock) child.accept(this)
                if (child is Block && child !is FencedCodeBlock && child !is IndentedCodeBlock) addBlock(htmlBlock(child))
                child = next
            }
        }
    }
}
