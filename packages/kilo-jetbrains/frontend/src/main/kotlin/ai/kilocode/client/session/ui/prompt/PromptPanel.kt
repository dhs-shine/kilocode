package ai.kilocode.client.session.ui.prompt

import ai.kilocode.client.actions.SendPromptAction
import ai.kilocode.client.actions.StopSessionAction
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.ReasoningPicker
import ai.kilocode.client.session.model.PromptAttachment
import ai.kilocode.client.session.model.PromptAttachmentExtractor
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.session.ui.mode.ModePicker
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.RoundedContentPanel
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.iconButton
import ai.kilocode.log.ChatLogSummary
import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.dto.PromptPartDto
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xml.util.XmlStringUtil
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/**
 * Prompt input panel with borderless IntelliJ editor text field and
 * mode/model controls grouped inside one rounded editor-background shell.
 */
class PromptPanel(
    private val project: Project,
    private val onSend: (String, List<PromptPartDto>) -> Unit,
    private val onAbort: () -> Unit,
) : BorderLayoutPanel(), SessionEditorStyleTarget, SendPromptContext {

    companion object {
        private val LOG = KiloLog.create(PromptPanel::class.java)
        private val SEND_ICON: Icon = IconLoader.getIcon("/icons/send.svg", PromptPanel::class.java)
        private val STOP_ICON: Icon = IconLoader.getIcon("/icons/stop.svg", PromptPanel::class.java)
        private val SHIELD_ICON: Icon = IconLoader.getIcon("/icons/shield.svg", PromptPanel::class.java)
        private val SHIELD_FILLED_ICON: Icon = IconLoader.getIcon("/icons/shield-filled.svg", PromptPanel::class.java)
    }

    val mode = ModePicker()
    val model = ModelPicker().apply {
        placement = ModelPicker.Placement.ABOVE
    }
    val reasoning = ReasoningPicker()
    var onReset: () -> Unit = {}
    var onChange: () -> Unit = {}
    var onAutoApproveToggle: (Boolean) -> Unit = {}
    var onFileDrag: (Boolean) -> Unit = {}
    private var style = SessionEditorStyle.current()
    private val shell = PromptShell()
    private val attachments = mutableListOf<PromptAttachment>()
    private val strip = PromptAttachmentStrip(project) { removeAttachment(it) }
    private var bus: MessageBusConnection? = null
    private var autoApprove = false
    private var attachment = true

    private val editor = PromptEditorTextField(project, this).apply {
        border = JBUI.Borders.empty()
        setFontInheritedFromLAF(false)
        setPlaceholder(placeholder())
        setShowPlaceholderWhenFocused(true)
        setOneLineMode(false)
        addSettingsProvider { ed ->
            style.applyToEditor(ed)
            ed.setBorder(JBUI.Borders.empty())
            ed.scrollPane.border = JBUI.Borders.empty()
            ed.scrollPane.viewportBorder = JBUI.Borders.empty()
            ed.backgroundColor = style.editorScheme.defaultBackground
            ed.scrollPane.background = style.editorScheme.defaultBackground
            ed.scrollPane.viewport.background = style.editorScheme.defaultBackground
            ed.settings.isUseSoftWraps = true
            ed.settings.isAdditionalPageAtBottom = false
            ed.scrollPane.horizontalScrollBarPolicy =
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            installFileDrop(ed.contentComponent, "editor")
            installFileDrop(ed.scrollPane, "scroll")
            ed.contentComponent.addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    shell.repaint()
                }

                override fun focusLost(e: FocusEvent) {
                    shell.repaint()
                }
            })
        }
    }

    private val button: SendButton = SendButton().apply {
        icon = SEND_ICON
        isFocusPainted = false
        addActionListener {
            syncTooltip()
            val id = if (busy) StopSessionAction.ID else SendPromptAction.ID
            val action = ActionManager.getInstance().getAction(id)
                ?: return@addActionListener
            val ctx = DataManager.getInstance().getDataContext(button)
            val event = AnActionEvent.createEvent(action, ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            ActionUtil.updateAction(action, event)
            ActionUtil.performAction(action, event)
        }
    }

    private val reset = HoverIcon().apply {
        icon = AllIcons.Actions.Cancel
        toolTipText = KiloBundle.message("model.picker.reset")
        accessibleContext.accessibleName = KiloBundle.message("model.picker.reset")
        isVisible = false
        addActionListener { onReset() }
    }

    private val auto = AutoApproveButton().apply {
        icon = SHIELD_ICON
        addActionListener { onAutoApproveToggle(!autoApprove) }
    }

    @Volatile
    private var busy = false
    private var ready = false

    override val isSendEnabled: Boolean
        get() = ready && !busy && (text().isNotEmpty() || attachments.isNotEmpty())

    override val isStopEnabled: Boolean
        get() = busy

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(JBUI.CurrentTheme.ToolWindow.borderColor()),
            JBUI.Borders.empty(
                JBUI.scale(SessionUiStyle.View.Prompt.PANEL_VERTICAL_PADDING),
                JBUI.scale(SessionUiStyle.View.Prompt.PANEL_HORIZONTAL_PADDING),
                JBUI.scale(SessionUiStyle.View.Prompt.PANEL_VERTICAL_PADDING),
                JBUI.scale(SessionUiStyle.View.Prompt.PANEL_HORIZONTAL_PADDING),
            ),
        )

        applyStyle(style)
        editor.text = ""
        editor.addDocumentListener(object : DocumentListener {
            override fun documentChanged(e: DocumentEvent) {
                syncEditorHeight()
                onChange()
            }
        })
        shell.add(strip, BorderLayout.NORTH)
        shell.add(editor, BorderLayout.CENTER)

        val bar = BorderLayoutPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyTop(JBUI.scale(SessionUiStyle.View.Prompt.CONTROL_GAP))
        }
        bar.add(mode)
        bar.add(Box.createHorizontalStrut(JBUI.scale(SessionUiStyle.View.Prompt.CONTROL_GAP)))
        bar.add(model)
        bar.add(Box.createHorizontalStrut(JBUI.scale(SessionUiStyle.View.Prompt.CONTROL_GAP)))
        bar.add(reasoning)
        bar.add(Box.createHorizontalStrut(JBUI.scale(SessionUiStyle.View.Prompt.CONTROL_GAP)))
        bar.add(reset)
        bar.add(Box.createHorizontalGlue())
        bar.add(auto)
        bar.add(Box.createHorizontalStrut(JBUI.scale(SessionUiStyle.View.Prompt.CONTROL_GAP)))
        bar.add(button)
        shell.add(bar, BorderLayout.SOUTH)
        add(shell, BorderLayout.CENTER)
        installFileDrop(shell, "shell")
        syncTooltip()
        syncAutoApprove()
    }

    @RequiresEdt
    fun setReady(value: Boolean) {
        ready = value
    }

    @RequiresEdt
    fun setAttachmentEnabled(value: Boolean) {
        attachment = value
    }

    @RequiresEdt
    fun setBusy(value: Boolean) {
        busy = value
        button.icon = if (value) STOP_ICON else SEND_ICON
        syncTooltip()
    }

    @RequiresEdt
    fun setAutoApprove(value: Boolean) {
        if (autoApprove == value) return
        autoApprove = value
        syncAutoApprove()
    }

    @RequiresEdt
    fun setResetVisible(value: Boolean) {
        reset.isVisible = value
        revalidate()
        repaint()
    }

    @RequiresEdt
    fun text(): String = editor.text.trim()

    @RequiresEdt
    override fun send() {
        submit("action")
    }

    @RequiresEdt
    override fun stop() {
        if (!isStopEnabled) return
        onAbort()
    }

    internal fun inputFont() = editor.font

    internal fun resetVisibleForTest() = reset.isVisible

    internal fun resetForTest(): JComponent = reset

    internal fun shellForTest(): JComponent = shell

    internal fun buttonForTest(): JButton = button

    internal fun attachmentCountForTest(): Int = attachments.size

    internal val defaultFocusedComponent: JComponent get() = editor

    @RequiresEdt
    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
        editor.font = style.editorFont
        editor.getEditor(false)?.let(style::applyToEditor)
        editor.background = style.editorScheme.defaultBackground
        syncEditorHeight()
        syncAutoApprove()
    }

    @RequiresEdt
    fun clear() {
        editor.text = ""
        attachments.clear()
        strip.clear()
        syncEditorHeight()
    }

    @RequiresEdt
    fun addAttachmentForTest(item: PromptAttachment) {
        addAttachment(item)
    }

    @RequiresEdt
    fun focus() {
        editor.requestFocusInWindow()
    }

    override fun addNotify() {
        super.addNotify()
        bindKeymap()
    }

    override fun removeNotify() {
        bus?.disconnect()
        bus = null
        super.removeNotify()
    }

    @RequiresEdt
    private fun submit(src: String) {
        if (!isSendEnabled) return
        val txt = text()
        val files = attachments.map { it.part() }
        LOG.debug { "${ChatLogSummary.prompt(promptDto(txt, files))} src=$src busy=$busy" }
        onSend(txt, files)
    }

    @RequiresEdt
    private fun addAttachment(item: PromptAttachment) {
        if (!attachment && PromptAttachmentExtractor.media(item.mime)) {
            LOG.debug { "kind=prompt-attachment add name=${item.name} mime=${item.mime} blocked=unsupported-model" }
            notify(KiloBundle.message("prompt.attachment.unsupported.model"))
            return
        }
        if (attachments.any { it.id == item.id }) {
            LOG.debug { "kind=prompt-attachment add name=${item.name} mime=${item.mime} blocked=duplicate" }
            return
        }
        attachments += item
        strip.add(item)
        LOG.debug { "kind=prompt-attachment add name=${item.name} mime=${item.mime} count=${attachments.size}" }
        onChange()
    }

    @RequiresEdt
    private fun removeAttachment(item: PromptAttachment) {
        if (!attachments.removeIf { it.id == item.id }) return
        strip.remove(item)
        onChange()
    }

    private fun promptDto(text: String, files: List<PromptPartDto>) = ai.kilocode.rpc.dto.PromptDto(
        parts = buildList {
            text.takeIf { it.isNotBlank() }?.let { add(PromptPartDto(type = "text", text = it)) }
            addAll(files)
        }
    )

    internal fun installFileDrop(target: JComponent, area: String) {
        LOG.debug { "kind=prompt-dnd install area=$area component=${target.javaClass.name}" }
        DnDSupport.createBuilder(target)
            .enableAsNativeTarget()
            .setTargetChecker { event ->
                if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
                    onFileDrag(false)
                    LOG.debug { "kind=prompt-dnd check area=$area accept=false flavor=false" }
                    return@setTargetChecker true
                }
                event.setDropPossible(true)
                onFileDrag(true)
                LOG.debug { "kind=prompt-dnd check area=$area accept=true flavor=true" }
                false
            }
            .setCleanUpOnLeaveCallback {
                onFileDrag(false)
            }
            .setDropHandlerWithResult { event ->
                val start = System.nanoTime()
                val files = dropFiles(event)
                val ms = elapsedMs(start)
                LOG.debug { "kind=prompt-dnd drop area=$area files=${files.size} extractMs=$ms queued=${files.isNotEmpty()}" }
                onFileDrag(false)
                if (files.isEmpty()) return@setDropHandlerWithResult false
                processDrop(files, area, ms)
                true
            }
            .install()
    }

    private fun processDrop(files: List<java.io.File>, area: String, dropMs: Long) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val start = System.nanoTime()
            try {
                val items = PromptAttachmentExtractor.files(files)
                val ms = elapsedMs(start)
                LOG.debug { "kind=prompt-dnd extract area=$area files=${files.size} attachments=${items.size} extractMs=$ms dropMs=$dropMs" }
                if (items.isEmpty()) return@executeOnPooledThread
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed || !isDisplayable) return@invokeLater
                    LOG.debug { "kind=prompt-dnd attach area=$area files=${files.size} attachments=${items.size} extractMs=$ms dropMs=$dropMs" }
                    items.forEach(::addAttachment)
                }
            } catch (e: Exception) {
                LOG.warn("kind=prompt-dnd extract area=$area files=${files.size} failed message=${e.message}", e)
            }
        }
    }

    private fun dropFiles(event: DnDEvent): List<java.io.File> {
        if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) return emptyList()
        return FileCopyPasteUtil.getFileListFromAttachedObject(event.attachedObject).orEmpty()
    }

    private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000

    private fun notify(text: String) {
        com.intellij.notification.Notification("Kilo Code", text, com.intellij.notification.NotificationType.WARNING).notify(project)
    }

    @RequiresEdt
    private fun bindKeymap() {
        if (bus != null) return
        val connection = ApplicationManager.getApplication().messageBus.connect()
        bus = connection
        connection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
            override fun activeKeymapChanged(keymap: Keymap?) {
                editor.setPlaceholder(placeholder())
                syncTooltip()
            }

            override fun shortcutsChanged(keymap: Keymap, actionIds: Collection<String>, fromSettings: Boolean) {
                if (SendPromptAction.ID in actionIds || StopSessionAction.ID in actionIds ||
                    IdeActions.ACTION_EDITOR_START_NEW_LINE in actionIds) {
                    editor.setPlaceholder(placeholder())
                    syncTooltip()
                }
            }
        })
    }

    @RequiresEdt
    private fun syncTooltip() {
        button.toolTipText = tooltip()
    }

    private fun syncAutoApprove() {
        auto.isSelected = autoApprove
        auto.icon = if (autoApprove) SHIELD_FILLED_ICON else SHIELD_ICON
        auto.toolTipText = if (autoApprove) {
            KiloBundle.message("prompt.action.autoApprove.enabled.tooltip")
        } else {
            KiloBundle.message("prompt.action.autoApprove.disabled.tooltip")
        }
        auto.accessibleContext.accessibleName = if (autoApprove) {
            KiloBundle.message("prompt.action.autoApprove.disable")
        } else {
            KiloBundle.message("prompt.action.autoApprove.enable")
        }
        auto.repaint()
    }

    private fun tooltip(): String {
        val id = if (busy) StopSessionAction.ID else SendPromptAction.ID
        val text = if (busy) {
            KiloBundle.message("prompt.button.stop")
        } else {
            KiloBundle.message("prompt.button.send")
        }
        val tip = KeymapUtil.createTooltipText(text, id)
        if (busy) return tip
        val stop = KeymapUtil.getFirstKeyboardShortcutText(StopSessionAction.ID)
        if (stop.isEmpty()) return tip
        return XmlStringUtil.wrapInHtml(
            XmlStringUtil.escapeString(tip) + "<br>" +
                XmlStringUtil.escapeString(KiloBundle.message("prompt.button.send.tooltip.stop", stop))
        )
    }

    private fun placeholder(): String {
        val send = KeymapUtil.getFirstKeyboardShortcutText(SendPromptAction.ID)
        val line = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_EDITOR_START_NEW_LINE)
        if (send.isNotEmpty() && line.isNotEmpty()) {
            return KiloBundle.message("prompt.placeholder.with.shortcuts", send, line)
        }
        if (send.isNotEmpty()) return KiloBundle.message("prompt.placeholder.with.send", send)
        if (line.isNotEmpty()) return KiloBundle.message("prompt.placeholder.with.newline", line)
        return KiloBundle.message("prompt.placeholder")
    }

    @RequiresEdt
    private fun syncEditorHeight() {
        val count = ApplicationManager.getApplication().runReadAction<Int> { editor.document.lineCount }
        val lines = (count + SessionUiStyle.View.Prompt.EDITOR_SPARE_LINES).coerceIn(
            SessionUiStyle.View.Prompt.EDITOR_LINES,
            SessionUiStyle.View.Prompt.EDITOR_MAX_LINES,
        )
        val height = style.editorFont.size * lines + JBUI.scale(SessionUiStyle.View.Prompt.EDITOR_CHROME)
        if (editor.preferredSize.height == height && editor.minimumSize.height == height) return
        editor.preferredSize = JBDimension(0, height)
        editor.minimumSize = JBDimension(0, height)
        revalidate()
        repaint()
    }

    private inner class SendButton : JButton(), UiDataProvider {
        private var over = false

        init {
            iconButton(this)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    sync(true)
                }

                override fun mouseExited(e: MouseEvent) {
                    sync(false)
                }
            })
        }

        override fun getPreferredSize() = JBUI.size(
            SessionUiStyle.View.Prompt.SEND_BUTTON_SIZE,
            SessionUiStyle.View.Prompt.SEND_BUTTON_SIZE,
        )

        override fun uiDataSnapshot(sink: DataSink) {
            sink.set(PromptDataKeys.SEND, this@PromptPanel)
        }

        override fun getMinimumSize() = preferredSize

        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            if (over) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                    val arc = JBUI.scale(JBUI.getInt("Button.arc", SessionUiStyle.View.Prompt.CORNER_ARC))
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }

        private fun sync(value: Boolean) {
            if (over == value) return
            over = value
            repaint()
        }
    }

    private inner class AutoApproveButton : JButton() {
        private var over = false

        init {
            iconButton(this)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    sync(true)
                }

                override fun mouseExited(e: MouseEvent) {
                    sync(false)
                }
            })
        }

        override fun getPreferredSize() = JBUI.size(
            SessionUiStyle.View.Prompt.SEND_BUTTON_SIZE,
            SessionUiStyle.View.Prompt.SEND_BUTTON_SIZE,
        )

        override fun getMinimumSize() = preferredSize

        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            if (over) paintHover(g)
            super.paintComponent(g)
        }

        private fun paintHover(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                val arc = JBUI.scale(JBUI.getInt("Button.arc", SessionUiStyle.View.Prompt.CORNER_ARC))
                g2.fillRoundRect(0, 0, width, height, arc, arc)
            } finally {
                g2.dispose()
            }
        }

        private fun sync(value: Boolean) {
            if (over == value) return
            over = value
            repaint()
        }
    }

    private inner class PromptShell : RoundedContentPanel(
        JBUI.scale(SessionUiStyle.View.Prompt.SHELL_VERTICAL_PADDING),
        JBUI.scale(SessionUiStyle.View.Prompt.SHELL_HORIZONTAL_PADDING),
    ) {
        private val focus = JBValue.UIInteger("Component.focusWidth", SessionUiStyle.View.Prompt.FOCUS_WIDTH)

        override fun contentColor() = style.editorScheme.defaultBackground

        override fun outlineColor() = if (UIUtil.isFocusAncestor(editor)) {
            JBUI.CurrentTheme.Focus.focusColor()
        } else {
            SessionUiStyle.View.line()
        }

        override fun outlineWidth() = if (UIUtil.isFocusAncestor(editor)) focus.get() else JBUI.scale(1)

        override fun cornerArc() = JBUI.scale(JBUI.getInt("Button.arc", SessionUiStyle.View.Prompt.CORNER_ARC))
    }
}
