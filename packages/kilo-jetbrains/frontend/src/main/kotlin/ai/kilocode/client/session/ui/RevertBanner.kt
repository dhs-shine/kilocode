package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.SessionModel
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.views.base.BaseQuestionView
import ai.kilocode.client.ui.DiffStatBadge
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout

class RevertBanner(
    private val model: SessionModel,
    private val redoAction: () -> Unit,
    private val redoAllAction: () -> Unit,
) : BorderLayoutPanel(), SessionView, SessionEditorStyleTarget {
    override val sessionViewKind = SessionView.Kind.Default

    private val card = BaseQuestionView()

    private val body = Stack.vertical(UiStyle.Gap.lg())

    private val files = Stack.vertical(UiStyle.Gap.xs())

    private val hint = JBLabel(KiloBundle.message("revert.banner.hint")).apply {
        font = JBFont.small()
    }

    init {
        isOpaque = false
        body.isOpaque = false
        files.isOpaque = false
        card.setHeaderIcon(AllIcons.Actions.Back, KiloBundle.message("revert.message.rollback"))
        body.next(files).next(hint)
        card.setContent(body)
        card.setActions(listOf(
            BaseQuestionView.Action("redo", KiloBundle.message("revert.banner.redo"), primary = false) { redoAction() },
            BaseQuestionView.Action("all", KiloBundle.message("revert.banner.redo.all"), primary = false) { redoAllAction() },
        ))
        add(card, BorderLayout.CENTER)
        applyStyle(SessionEditorStyle.current())
        update()
    }

    @RequiresEdt
    fun update() {
        val revert = model.revert()
        isVisible = revert != null
        if (revert == null) return
        val total = model.revertedCount()
        card.setHeader(KiloBundle.message(if (total == 1) "revert.banner.count.one" else "revert.banner.count.other", total))
        card.setActionVisible("all", total > 1)
        files.removeAll()
        for (file in model.diff) {
            val row = Stack.horizontal(UiStyle.Gap.sm())
                .next(JBLabel(file.file).apply { foreground = UIUtil.getLabelForeground() })
                .next(DiffStatBadge(file.additions, file.deletions))
            files.next(row)
        }
        revalidate()
        repaint()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        card.applyStyle(style)
        hint.foreground = UIUtil.getLabelForeground()
    }
}
