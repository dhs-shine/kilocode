package ai.kilocode.client.session.views.base

import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Generic
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.ui.UiStyle
import com.intellij.ui.components.JBLabel

/**
 * Fallback renderer for part types that have no dedicated view.
 *
 * Rather than silently dropping unknown content (which could lead to
 * confusing empty gaps), this shows a dim label with the raw type name.
 * This makes it easy to spot new part types that need a proper renderer.
 */
class GenericView(content: Generic) : SecondarySessionPartView(JBLabel("[${content.type}]"), JBLabel()) {

    override val contentId: String = content.id

    private val label = row.getComponent(0) as JBLabel

    init {
        label.foreground = UiStyle.Colors.weak()
        applyStyle(SessionEditorStyle.current())
        syncExpandable(false)
    }

    override fun update(content: Content) {}  // generic content has no updatable state

    /** Exposed for tests. */
    fun labelText(): String = label.text

    override fun applyStyle(style: SessionEditorStyle) {
        if (label.font == style.smallFont) return
        label.font = style.smallFont
        revalidate()
        repaint()
    }

    override fun dumpLabel() = "GenericView#$contentId(${label.text})"
}
