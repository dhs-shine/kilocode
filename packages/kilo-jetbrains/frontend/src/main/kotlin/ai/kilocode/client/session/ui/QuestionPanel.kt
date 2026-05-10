package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.update.SessionController
import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.QuestionReplyDto
import com.intellij.icons.AllIcons
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout

/**
 * Docked question panel — shown above the prompt when the session is in
 * [ai.kilocode.client.session.model.SessionState.AwaitingQuestion].
 *
 * Renders all [Question.items] with per-item option selection.
 * For `multiple = false` items, selecting an option replaces the previous choice.
 * For `multiple = true` items, option buttons toggle membership.
 *
 * A single Submit button at the bottom sends all collected answers once each
 * item has at least one answer selected.
 */
class QuestionPanel(
    private val controller: SessionController,
) : BorderLayoutPanel() {

    private var requestId: String? = null

    init {
        border = UiStyle.Dock.neutral()
        isVisible = false
    }

    /** Populate the panel for all items in [question] and make it visible. */
    fun show(question: Question) {
        if (question.items.isEmpty()) {
            hidePanel()
            return
        }
        requestId = question.id

        // Per-item selected answers: index → mutable set of selected labels
        val selections = Array(question.items.size) { mutableSetOf<String>() }

        removeAll()
        add(panel {
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
                    reply(selections.map { it.toList() })
                }.gap(RightGap.SMALL)
                button(KiloBundle.message("session.question.dismiss")) { reject() }
            }.layout(RowLayout.INDEPENDENT).topGap(TopGap.SMALL)
        }, BorderLayout.CENTER)

        isVisible = true
        revalidate()
        repaint()
    }

    /** Hide this panel. */
    fun hidePanel() {
        requestId = null
        removeAll()
        isVisible = false
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

    private fun reply(answers: List<List<String>>) {
        val id = requestId ?: return
        controller.replyQuestion(id, QuestionReplyDto(answers))
        hidePanel()
    }

    private fun reject() {
        val id = requestId ?: return
        controller.rejectQuestion(id)
        hidePanel()
    }
}
