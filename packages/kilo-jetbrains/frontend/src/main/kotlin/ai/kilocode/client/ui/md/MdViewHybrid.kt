package ai.kilocode.client.ui.md

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.selection.SessionSelection
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.log.KiloLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
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
import java.awt.Dimension
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
    style: SessionEditorStyle = SessionEditorStyle.current(),
    private var selection: SessionSelection? = null,
) : MdView {
    companion object {
        private val LOG = KiloLog.create(MdViewHybrid::class.java)

        private val FILES = mapOf(
            "kt" to "kt",
            "kotlin" to "kt",
            "js" to "js",
            "javascript" to "js",
            "jsx" to "jsx",
            "ts" to "ts",
            "typescript" to "ts",
            "tsx" to "tsx",
            "java" to "java",
            "py" to "py",
            "python" to "py",
            "sh" to "sh",
            "bash" to "sh",
            "shell" to "sh",
            "json" to "json",
            "xml" to "xml",
            "html" to "html",
            "css" to "css",
            "md" to "md",
            "markdown" to "md",
            "yaml" to "yaml",
            "yml" to "yaml",
            "toml" to "toml",
            "go" to "go",
            "golang" to "go",
            "rs" to "rs",
            "rust" to "rs",
            "rb" to "rb",
            "ruby" to "rb",
            "php" to "php",
            "swift" to "swift",
            "scala" to "scala",
            "sql" to "sql",
            "dockerfile" to "dockerfile",
            "docker" to "dockerfile",
            "gradle" to "gradle",
            "kts" to "kts",
            "c" to "c",
            "h" to "h",
            "cpp" to "cpp",
            "c++" to "cpp",
            "cc" to "cc",
            "cxx" to "cxx",
            "hpp" to "hpp",
            "h++" to "hpp",
            "cs" to "cs",
            "csharp" to "cs",
            "c#" to "cs",
            "fs" to "fs",
            "fsharp" to "fs",
            "f#" to "fs",
            "ps1" to "ps1",
            "powershell" to "ps1",
            "pwsh" to "ps1",
            "bat" to "bat",
            "batch" to "bat",
            "cmd" to "bat",
            "makefile" to "makefile",
            "make" to "makefile",
            "terraform" to "tf",
            "tf" to "tf",
            "hcl" to "hcl",
            "vue" to "vue",
            "svelte" to "svelte",
            "graphql" to "graphql",
            "proto" to "proto",
            "ini" to "ini",
            "properties" to "properties",
            "diff" to "diff",
            "patch" to "patch",
        )
    }

    private val listeners = mutableListOf<MdView.LinkListener>()
    private val source = StringBuilder()
    private var style = style
    private var rendered = ""
    private var block: Disposable? = null
    private var disposed = false

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
            if (disposed) return
            if (fontOverride == value) return
            fontOverride = value
            syncStyle()
        }

    override var foreground: Color
        get() = foregroundOverride ?: opts().foreground
        set(value) {
            if (disposed) return
            if (foregroundOverride == value) return
            foregroundOverride = value
            syncStyle()
        }

    override var background: Color
        get() = backgroundOverride ?: opts().background
        set(value) {
            if (disposed) return
            if (backgroundOverride == value) return
            backgroundOverride = value
            syncStyle()
        }

    override var linkColor: Color
        get() = linkColorOverride ?: opts().linkColor
        set(value) {
            if (disposed) return
            if (linkColorOverride == value) return
            linkColorOverride = value
            syncStyle()
        }

    override var codeBg: Color
        get() = codeBgOverride ?: opts().codeBg
        set(value) {
            if (disposed) return
            if (codeBgOverride == value) return
            codeBgOverride = value
            syncStyle()
        }

    override var preBg: Color
        get() = preBgOverride ?: opts().preBg
        set(value) {
            if (disposed) return
            if (preBgOverride == value) return
            preBgOverride = value
            syncStyle()
        }

    override var preFg: Color
        get() = preFgOverride ?: opts().preFg
        set(value) {
            if (disposed) return
            if (preFgOverride == value) return
            preFgOverride = value
            syncStyle()
        }

    override var codeFont: String
        get() = codeFontOverride ?: opts().codeFont
        set(value) {
            if (disposed) return
            if (codeFontOverride == value) return
            codeFontOverride = value
            syncStyle()
        }

    override var quoteBorder: Color
        get() = quoteBorderOverride ?: opts().quoteBorder
        set(value) {
            if (disposed) return
            if (quoteBorderOverride == value) return
            quoteBorderOverride = value
            syncStyle()
        }

    override var quoteFg: Color
        get() = quoteFgOverride ?: opts().quoteFg
        set(value) {
            if (disposed) return
            if (quoteFgOverride == value) return
            quoteFgOverride = value
            syncStyle()
        }

    override var tableBorder: Color
        get() = tableBorderOverride ?: opts().tableBorder
        set(value) {
            if (disposed) return
            if (tableBorderOverride == value) return
            tableBorderOverride = value
            syncStyle()
        }

    override var opaque: Boolean
        get() = opaqueState
        set(value) {
            if (disposed) return
            if (opaqueState == value) return
            opaqueState = value
            syncStyle()
        }

    override fun applyStyle(style: SessionEditorStyle) {
        if (disposed) return
        if (this.style == style) return
        this.style = style
        selection?.applyStyle(style)
        syncStyle()
    }

    override fun setSelection(selection: SessionSelection?) {
        if (disposed) return
        if (this.selection === selection) return
        this.selection = selection
        syncBlocks()
    }

    override fun resetStyles() {
        if (disposed) return
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
        if (disposed) return
        if (source.toString() == text) return
        source.clear()
        source.append(text)
        syncBlocks()
    }

    override fun append(delta: String) {
        if (disposed) return
        if (delta.isEmpty()) return
        source.append(delta)
        syncBlocks()
    }

    override fun clear() {
        if (disposed) return
        if (source.isEmpty() && rendered.isEmpty() && root.componentCount == 0) return
        source.clear()
        rendered = ""
        clearBlocks()
        root.revalidate()
        root.repaint()
    }

    override fun addLinkListener(listener: MdView.LinkListener) {
        if (disposed) return
        listeners.add(listener)
    }

    override fun removeLinkListener(listener: MdView.LinkListener) {
        listeners.remove(listener)
    }

    override fun markdown(): String = source.toString()

    override fun html(): String = rendered

    override fun overrideSheet(): String = MdCommon.rules(opts())

    override fun simulateLink(href: String) {
        if (disposed) return
        dispatch(MdView.LinkEvent(href))
    }

    override fun dispose() {
        disposed = true
        listeners.clear()
        source.clear()
        rendered = ""
        clearBlocks()
    }

    private fun syncStyle() {
        if (disposed) return
        val opts = opts()
        root.isOpaque = opts.opaque
        if (opts.opaque) root.background = opts.background
        syncBlocks()
    }

    private fun syncBlocks() {
        if (disposed) return
        val text = source.toString()
        val doc = parser.parse(text)
        val body = renderer.render(doc)
        rendered = body
        resetBlocks()
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

    private fun resetBlocks() {
        block?.let { Disposer.dispose(it) }
        block = Disposer.newDisposable("Markdown blocks")
        root.removeAll()
    }

    private fun clearBlocks() {
        block?.let { Disposer.dispose(it) }
        block = null
        root.removeAll()
    }

    private fun addGap() {
        if (root.componentCount == 0) return
        root.add(Box.createVerticalStrut(JBUI.scale(SessionUiStyle.View.Code.BLOCK_GAP)))
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
            block?.let { selection?.register(this, it) }
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
        val value = text.trimEnd('\n')
        val field = runCatching {
            CodeField(file(lang), opts, text).also { ed ->
                block?.let { ed.setDisposedWith(it) }
                block?.let { selection?.register(ed, it) }
            }
        }.getOrElse { err ->
            LOG.warn("kind=markdown codeEditor=true failed message=${err.message}", err)
            textArea(text, opts)
        }
        val height = codeHeight(field, value)
        val width = codeWidth(field, value)
        field.preferredSize = Dimension(width, height)
        field.minimumSize = Dimension(0, height)
        field.maximumSize = Dimension(Int.MAX_VALUE, height)
        return object : JBScrollPane(field) {
            override fun doLayout() {
                super.doLayout()
                val view = viewport.view ?: return
                val size = viewport.extentSize
                if (size.height <= 0 || view.height == size.height) return
                view.setSize(view.width.coerceAtLeast(size.width), size.height)
            }
        }.apply {
            border = JBUI.Borders.customLine(opts.tableBorder, SessionUiStyle.View.Code.BORDER_WIDTH)
            viewportBorder = JBUI.Borders.empty(
                SessionUiStyle.View.Code.topPadding(),
                SessionUiStyle.View.Code.VIEWPORT_HORIZONTAL_PADDING,
                SessionUiStyle.View.Code.VIEWPORT_BOTTOM_PADDING,
                SessionUiStyle.View.Code.VIEWPORT_HORIZONTAL_PADDING,
            )
            isOpaque = opts.opaque
            background = opts.preBg
            viewport.background = opts.preBg
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            isWheelScrollingEnabled = true
            setOverlappingScrollBar(false)
            horizontalScrollBar.preferredSize = Dimension(0, JBUI.scale(SessionUiStyle.View.Code.SCROLLBAR_HEIGHT))
            horizontalScrollBar.isOpaque = true
            verticalScrollBar.preferredSize = JBUI.emptySize()
            val pad = viewportBorder.getBorderInsets(this)
            val size = height + insets.top + insets.bottom + pad.top + pad.bottom + horizontalScrollBar.preferredSize.height
            preferredSize = Dimension(0, size)
            minimumSize = Dimension(0, size)
            maximumSize = Dimension(Int.MAX_VALUE, size)
        }
    }

    private fun codeWidth(component: JComponent, text: String): Int {
        val metrics = component.getFontMetrics(component.font)
        val width = text.lineSequence().maxOfOrNull { metrics.stringWidth(it) } ?: 0
        return width + JBUI.scale(SessionUiStyle.View.Code.WIDTH_PADDING)
    }

    private fun codeHeight(component: JComponent, text: String): Int {
        val count = text.lineSequence().count()
        val rows = count.coerceAtLeast(SessionUiStyle.View.Code.MIN_ROWS)
        val field = component as? CodeField
        if (field != null) {
            field.ensureWillComputePreferredSize()
            val ed = field.getEditor(false)
            val line = ed?.lineHeight ?: component.getFontMetrics(component.font).height
            return maxOf(field.preferredSize.height, line * rows)
        }
        val line = component.getFontMetrics(component.font).height
        return line * rows
    }

    private fun textArea(text: String, opts: MdStyle) = JBTextArea(text.trimEnd('\n')).apply {
        isEditable = false
        lineWrap = false
        isOpaque = opts.opaque
        background = opts.preBg
        foreground = opts.preFg
        font = Font(opts.codeFont, Font.PLAIN, style.editorSize)
        border = JBUI.Borders.empty(
            SessionUiStyle.View.Code.VIEWPORT_TOP_PADDING,
            SessionUiStyle.View.Code.VIEWPORT_HORIZONTAL_PADDING,
        )
        block?.let { selection?.register(this, it) }
    }

    private inner class CodeField(file: FileType, opts: MdStyle, value: String) :
        com.intellij.ui.EditorTextField(
            EditorFactory.getInstance().createDocument(value.trimEnd('\n')),
            ProjectManager.getInstance().defaultProject,
            file,
            true,
            false,
        ) {
        init {
            setFontInheritedFromLAF(false)
            font = Font(opts.codeFont, Font.PLAIN, style.editorSize)
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
                ed.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                ed.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            }
        }
    }

    private fun file(lang: String?): FileType {
        val key = lang?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase().orEmpty()
        val ext = FILES[key] ?: return PlainTextFileType.INSTANCE
        val type = FileTypeRegistry.getInstance().getFileTypeByExtension(ext)
        if (type == UnknownFileType.INSTANCE) return PlainTextFileType.INSTANCE
        return type
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
