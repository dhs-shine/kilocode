package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.SessionModel
import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.client.session.model.SessionState
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ScrollPaneConstants

/**
 * Progress and per-session error footer rendered at the bottom of the session transcript.
 *
 * Reacts to [SessionModelEvent.StateChanged]:
 * - [SessionState.Busy] shows an animated spinner and status text
 * - [SessionState.Error] shows an inline error banner with optional expandable detail
 * - Any other state hides the footer
 */
class ProgressPanel(
    model: SessionModel,
    parent: Disposable,
) : BorderLayoutPanel(), SessionEditorStyleTarget {

    companion object {
        private const val DETAILS_LINES = 10
        private const val DETAILS_MAX = 4_000
        private const val CHROME = 2
    }

    private val click = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            flip()
        }
    }

    private val header = BorderLayoutPanel()

    private val left = BorderLayoutPanel().apply {
        layout = BorderLayout(UiStyle.Gap.sm(), 0)
        addMouseListener(click)
    }

    private val toggle = JBLabel().apply {
        isVisible = false
        addMouseListener(click)
    }

    private val spinner = JBLabel(AnimatedIcon.Default())

    private val icon = Stack.horizontal().next(toggle).next(spinner)

    private val label = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
        addMouseListener(click)
    }

    private val details = JBTextArea().apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        foreground = UiStyle.Colors.fg()
    }

    private val scroll = JBScrollPane(details).apply {
        border = JBUI.Borders.empty(UiStyle.Gap.sm(), UiStyle.Gap.lg(), 0, 0)
        isOpaque = false
        viewport.isOpaque = false
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        isVisible = false
    }

    private var detail: String? = null
    private var expanded = false

    init {
        isVisible = false
        border = JBUI.Borders.empty(
            UiStyle.Gap.sm(),
            JBUI.scale(SessionUiStyle.View.Layout.HORIZONTAL_PADDING),
            0,
            0,
        )
        left.add(icon, BorderLayout.WEST)
        left.add(label, BorderLayout.CENTER)
        header.add(left, BorderLayout.CENTER)
        add(header, BorderLayout.NORTH)
        applyStyle(SessionEditorStyle.current())

        model.addListener(parent) { event ->
            if (event is SessionModelEvent.StateChanged) onState(event.state)
        }
    }

    /** Exposed for test assertions. */
    fun labelText(): String = label.text

    private fun onState(state: SessionState) {
        when (state) {
            is SessionState.Busy -> showBusy(state.text)
            is SessionState.Error -> showError(state)
            else -> hidePanel()
        }
    }

    private fun showBusy(text: String) {
        label.text = text
        label.foreground = UiStyle.Colors.weak()
        detail = null
        expanded = false
        spinner.isVisible = true
        toggle.isVisible = false
        syncDetails()
        showPanel()
    }

    private fun showError(state: SessionState.Error) {
        label.text = state.message
        label.foreground = UiStyle.Colors.errorLabelForeground()
        detail = detail(state)?.let(::preview)
        expanded = false
        spinner.isVisible = false
        toggle.isVisible = detail != null
        syncDetails()
        showPanel()
    }

    private fun syncDetails() {
        val text = detail
        val show = expanded && text != null
        val cursor = if (text != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        toggle.icon = if (expanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        left.cursor = cursor
        label.cursor = cursor
        toggle.cursor = cursor
        details.text = text ?: ""
        scroll.isVisible = show
        if (show) add(scroll, BorderLayout.CENTER)
        else remove(scroll)
    }

    private fun flip() {
        if (!toggle.isVisible) return
        expanded = !expanded
        syncDetails()
        refresh()
    }

    private fun showPanel() {
        if (!isVisible) isVisible = true
        refresh()
    }

    private fun hidePanel() {
        if (isVisible) isVisible = false
        detail = null
        expanded = false
        syncDetails()
        refresh()
    }

    private fun refresh() {
        parent?.revalidate()
        parent?.repaint()
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        if (!scroll.isVisible) return size
        return JBDimension(size.width, header.preferredSize.height + scrollHeight())
    }

    private fun scrollHeight(): Int {
        val rows = details.text.lineSequence().count().coerceIn(1, DETAILS_LINES)
        return details.getFontMetrics(details.font).height * rows + scrollChrome()
    }

    private fun scrollChrome() = scroll.insets.top + scroll.insets.bottom + JBUI.scale(CHROME)

    private fun preview(text: String): String {
        if (text.length <= DETAILS_MAX) return text
        return text.take(DETAILS_MAX).trimEnd() + "..."
    }

    private fun detail(state: SessionState.Error): String? {
        val items = mutableListOf<String>()
        state.statusCode?.let { items += KiloBundle.message("session.error.status", it) }
        state.kind?.takeIf { it.isNotBlank() && it != state.message }?.let { items += it }
        state.detail?.trim()?.takeIf { it.isNotBlank() }?.let { items += it }
        return items.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    override fun applyStyle(style: SessionEditorStyle) {
        label.font = style.regularFont
        details.font = style.regularFont
        refresh()
    }

    internal fun summaryColor() = label.foreground

    internal fun detailsText() = details.text

    internal fun detailsVisible() = scroll.isVisible

    internal fun toggleVisible() = toggle.isVisible

    internal fun toggleExpanded() = expanded

    internal fun spinnerVisible() = spinner.isVisible

    internal fun clickToggle() {
        if (!toggle.isVisible) return
        toggle.mouseListeners.firstOrNull()?.mouseClicked(
            MouseEvent(toggle, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false),
        )
    }
}
