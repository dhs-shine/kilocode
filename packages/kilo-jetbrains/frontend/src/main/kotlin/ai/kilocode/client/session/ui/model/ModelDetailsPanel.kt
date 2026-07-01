package ai.kilocode.client.session.ui.model

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal class ModelDetailsPanel(
    private val favorites: () -> Set<String>,
    private val toggle: (ModelPicker.Item) -> Unit,
) : JPanel(BorderLayout()) {
    private val title = JBLabel().apply { font = UiStyle.Fonts.bold() }
    private val provider = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
    private val star = JBLabel().apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        horizontalAlignment = JBLabel.CENTER
        verticalAlignment = JBLabel.CENTER
    }
    private val badges = Stack.horizontal(UiStyle.Gap.xs())
    private val body = Stack.vertical(UiStyle.Gap.sm()).apply {
        border = JBUI.Borders.empty(UiStyle.Gap.md(), UiStyle.Gap.lg(), UiStyle.Gap.md(), UiStyle.Gap.lg())
    }
    private val scroll = JBScrollPane(body).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        border = JBUI.Borders.empty()
        viewportBorder = JBUI.Borders.empty()
    }
    private var item: ModelPicker.Item? = null

    init {
        border = JBUI.Borders.customLineLeft(UIUtil.getBoundsColor())
        add(scroll, BorderLayout.CENTER)
        star.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                item?.let {
                    toggle(it)
                    update(it)
                }
            }
        })
    }

    fun update(value: ModelPicker.Item?) {
        item = value
        body.removeAll()
        if (value == null) {
            body.next(JBLabel(KiloBundle.message("model.picker.details.empty")).apply {
                foreground = UIUtil.getContextHelpForeground()
            })
            refresh()
            return
        }

        title.text = ModelText.parts(value).model
        provider.text = value.providerName
        star.icon = if (value.key in favorites()) AllIcons.Nodes.Favorite else AllIcons.Nodes.NotFavoriteOnHover
        star.toolTipText = if (value.key in favorites()) {
            KiloBundle.message("model.picker.favorite.remove")
        } else {
            KiloBundle.message("model.picker.favorite.add")
        }
        badges.removeAll()
        if (value.free && !value.byok) badges.next(badge(ModelText.freeLabel()))
        if (value.byok) badges.next(badge("BYOK"))
        if (ModelText.collectsData(value)) badges.next(badge(KiloBundle.message("model.picker.dataCollected")))
        if (value.latest == true) badges.next(badge(KiloBundle.message("model.picker.details.latest")))

        body.next(header())
        if (badges.componentCount > 0) body.next(badges)
        grid(value)?.let(body::next)
        bench(value)?.let(body::next)
        capabilities(value)?.let(body::next)
        description(value)?.let(body::next)
        routing(value)?.let(body::next)
        body.next(section(KiloBundle.message("model.picker.details.ids"), listOf(
            KiloBundle.message("model.picker.details.providerId") to value.provider,
            KiloBundle.message("model.picker.details.modelId") to value.id,
        )))
        refresh()
    }

    private fun header() = JPanel(BorderLayout()).apply {
        add(Stack.vertical(UiStyle.Gap.xs()).next(title).next(provider), BorderLayout.CENTER)
        add(star, BorderLayout.EAST)
    }

    private fun grid(item: ModelPicker.Item): JComponent? {
        val ctx = item.limit?.context?.takeIf { it > 0 } ?: item.contextLength?.takeIf { it > 0 }
        val rows = buildList {
            item.releaseDate?.let { add(KiloBundle.message("model.picker.details.released") to date(it)) }
            if (!item.free) {
                item.cost?.let { cost ->
                    add(KiloBundle.message("model.picker.details.input") to price(cost.input))
                    add(KiloBundle.message("model.picker.details.output") to price(cost.output))
                    add(KiloBundle.message("model.picker.details.cached") to cached(cost.input, cost.cache?.read))
                    add(KiloBundle.message("model.picker.details.average") to price(average(cost.input, cost.output, cost.cache?.read)))
                } ?: run {
                    item.inputPrice?.let { add(KiloBundle.message("model.picker.details.input") to price(it)) }
                    item.outputPrice?.let { add(KiloBundle.message("model.picker.details.output") to price(it)) }
                }
            }
            ctx?.let { add(KiloBundle.message("model.picker.details.context") to context(it)) }
        }
        if (rows.isEmpty()) return null
        return section(KiloBundle.message("model.picker.details.properties"), rows)
    }

    private fun bench(item: ModelPicker.Item): JComponent? {
        val bench = item.terminalBench ?: return null
        return section(KiloBundle.message("model.picker.details.terminalBench"), listOf(
            KiloBundle.message("model.picker.details.completion") to percent(bench.overallScore),
            KiloBundle.message("model.picker.details.costAttempt") to attempt(bench.avgAttemptCostUsd),
        ))
    }

    private fun capabilities(item: ModelPicker.Item): JComponent? {
        val cap = item.capabilities
        val values = buildList {
            if (cap?.reasoning == true || item.reasoning) add(KiloBundle.message("model.picker.details.reasoning"))
            val input = cap?.input
            if (input?.text == true) add(KiloBundle.message("model.picker.details.modality.text"))
            if (input?.image == true) add(KiloBundle.message("model.picker.details.modality.image"))
            if (input?.audio == true) add(KiloBundle.message("model.picker.details.modality.audio"))
            if (input?.video == true) add(KiloBundle.message("model.picker.details.modality.video"))
            if (input?.pdf == true) add(KiloBundle.message("model.picker.details.modality.pdf"))
            if (item.attachment) add(KiloBundle.message("model.picker.details.attachments"))
        }
        if (values.isEmpty()) return null
        return section(KiloBundle.message("model.picker.details.capabilities"), values.map { "" to it })
    }

    private fun description(item: ModelPicker.Item): JComponent? {
        val text = item.options?.description?.takeIf { it.isNotBlank() } ?: return null
        return Stack.vertical(UiStyle.Gap.xs())
            .next(heading(KiloBundle.message("model.picker.details.description")))
            .next(JBLabel(XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(text))).apply {
                foreground = UIUtil.getLabelForeground()
                setAllowAutoWrapping(true)
            })
    }

    private fun routing(item: ModelPicker.Item): JComponent? {
        val models = item.autoRouting?.models?.takeIf { it.isNotEmpty() } ?: return null
        return Stack.vertical(UiStyle.Gap.xs())
            .next(heading(KiloBundle.message("model.picker.details.autoRouting")))
            .next(JBLabel(XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(models.joinToString("\n")))).apply {
                foreground = UIUtil.getLabelForeground()
                setAllowAutoWrapping(true)
            })
    }

    private fun section(title: String, rows: List<Pair<String, String>>) = Stack.vertical(UiStyle.Gap.xs()).apply {
        next(heading(title))
        rows.forEach { (label, value) -> next(row(label, value)) }
    }

    private fun row(label: String, value: String) = JPanel(BorderLayout()).apply {
        if (label.isNotBlank()) add(JBLabel(label).apply { foreground = UIUtil.getContextHelpForeground() }, BorderLayout.WEST)
        add(JBLabel(value).apply { foreground = UIUtil.getLabelForeground() }, BorderLayout.EAST)
    }

    private fun heading(value: String) = JBLabel(value).apply { font = UiStyle.Fonts.bold() }

    private fun badge(value: String) = JBLabel(value).apply {
        border = JBUI.Borders.empty(UiStyle.Gap.xs(), UiStyle.Gap.sm(), UiStyle.Gap.xs(), UiStyle.Gap.sm())
        foreground = UIUtil.getLabelForeground()
    }

    private fun refresh() {
        body.revalidate()
        body.repaint()
    }
}

private fun context(value: Long): String {
    if (value >= 1_000_000) return "${format(value.toDouble() / 1_000_000.0)}M"
    if (value >= 1_000) return "${format(value.toDouble() / 1_000.0)}K"
    return value.toString()
}

private fun price(value: Double): String {
    if (value == 0.0) return KiloBundle.message("model.picker.free")
    val digits = if (value < 0.01) 4 else 2
    return "$${value.format(digits)}/1M"
}

private fun cached(input: Double, read: Double?): String {
    if (read != null && read > 0.0) return price(read)
    if (input == 0.0) return price(0.0)
    return KiloBundle.message("model.picker.details.notSupported")
}

private fun average(input: Double, output: Double, read: Double?): Double {
    if (read != null && read > 0.0) return read * 0.7 + input * 0.2 + output * 0.1
    return input * 0.9 + output * 0.1
}

private fun percent(value: Double) = "${(value * 100.0).format(1)}%"

private fun attempt(value: Double) = "$${value.format(2)}"

private fun date(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM yyyy"))
}.getOrDefault(value)

private fun format(value: Double): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 1
}.format(value)

private fun Double.format(digits: Int) = String.format(Locale.US, "%.${digits}f", this)
