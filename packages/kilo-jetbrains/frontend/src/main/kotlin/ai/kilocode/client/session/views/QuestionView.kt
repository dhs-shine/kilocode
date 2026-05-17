package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.session.ui.SessionView
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionEditorStyleTarget
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.rpc.dto.QuestionReplyDto
import com.intellij.icons.AllIcons
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout

/**
 * Transcript-style question view — rendered inside [ai.kilocode.client.session.ui.SessionMessageListPanel]
 * at the end of the transcript when the session is in
 * [ai.kilocode.client.session.model.SessionState.AwaitingQuestion].
 *
 * Unlike the old docked [ai.kilocode.client.session.ui.QuestionPanel], this view lives inside
 * the scrollable transcript so the user can scroll through prior messages while a question is active.
 */
class QuestionView(
    private val reply: (String, QuestionReplyDto) -> Unit,
    private val reject: (String) -> Unit,
) : BorderLayoutPanel(), SessionEditorStyleTarget, SessionView {
    override val sessionViewKind = SessionView.Kind.Default

    private var requestId: String? = null
    private var style = SessionEditorStyle.current()

    init {
        isOpaque = false
        isVisible = false
    }

    /** Populate the view for all items in [question] and make it visible. */
    fun show(question: Question) {
        if (question.items.isEmpty()) {
            hideView()
            return
        }
        requestId = question.id

        // Per-item selected answers: index → mutable set of selected labels
        val selections = Array(question.items.size) { mutableSetOf<String>() }

        removeAll()

        val card = BorderLayoutPanel()
        card.isOpaque = true
        card.background = SessionUiStyle.View.surface()
        card.border = SessionUiStyle.View.card()

        card.add(panel {
            for ((idx, item) in question.items.withIndex()) {
                if (idx > 0) row { }.topGap(TopGap.SMALL)
                row {
                    icon(AllIcons.General.QuestionDialog).gap(RightGap.SMALL)
                    label(item.header).bold()
                }
                row {
                    label(item.question)
                }
                row {
                    for (opt in item.options) {
                        button(opt.label) {
                            toggleOption(selections, idx, item, opt.label)
                        }
                            .gap(RightGap.SMALL)
                            .applyToComponent { toolTipText = opt.description }
                    }
                }.layout(RowLayout.INDEPENDENT)
            }

            row {
                button(KiloBundle.message("session.question.submit")) {
                    doReply(selections.map { it.toList() })
                }.gap(RightGap.SMALL)
                button(KiloBundle.message("session.question.dismiss")) { doReject() }
            }.layout(RowLayout.INDEPENDENT).topGap(TopGap.SMALL)
        }.also { it.isOpaque = false }, BorderLayout.CENTER)

        add(card, BorderLayout.CENTER)

        isVisible = true
        refresh()
    }

    /** Hide this view and clear the active request id. */
    fun hideView() {
        requestId = null
        removeAll()
        isVisible = false
        refresh()
    }

    override fun applyStyle(style: SessionEditorStyle) {
        this.style = style
    }

    private fun toggleOption(
        selections: Array<MutableSet<String>>,
        idx: Int,
        item: QuestionItem,
        label: String,
    ) {
        val set = selections[idx]
        if (item.multiple) {
            if (!set.remove(label)) set.add(label)
        } else {
            set.clear()
            set.add(label)
        }
    }

    private fun doReply(answers: List<List<String>>) {
        val id = requestId ?: return
        reply(id, QuestionReplyDto(answers))
        hideView()
    }

    private fun doReject() {
        val id = requestId ?: return
        reject(id)
        hideView()
    }

    private fun refresh() {
        revalidate()
        repaint()
        parent?.revalidate()
        parent?.repaint()
    }
}
