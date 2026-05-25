package ai.kilocode.client.ui.layout

import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel

enum class HAlign { TRACK, FIT, LEFT, CENTER, RIGHT }
enum class VAlign { TRACK, FIT, TOP, CENTER, BOTTOM }

/**
 * A transparent wrapper panel that positions its single child according to independent
 * horizontal ([h]) and vertical ([v]) alignment modes.
 *
 * **TRACK**: child fills all available space on that axis, ignoring child min/preferred/max.
 * The wrapper reports zero contribution from the child on that axis for its own min/preferred/max.
 *
 * **FIT**: child fills available space clamped to child's effective [min, max] range.
 *
 * **LEFT / CENTER / RIGHT** (horizontal) and **TOP / CENTER / BOTTOM** (vertical):
 * child uses its bounded preferred size (coerced into [min, max]) and is placed at the
 * corresponding edge or centered. Shrinks to available space when necessary.
 *
 * During layout, the child is first sized to the available container space before
 * preferred size is read. This mirrors Swing layouts such as [java.awt.BorderLayout]
 * and lets wrapping components report a preferred height for the final width.
 *
 * Wrapper min/preferred/max sizes are computed by combining the per-axis child contribution
 * (zero for TRACK axes) with the panel insets.
 *
 * Use the factory extension for concise call sites:
 * ```
 * label.align(HAlign.CENTER, VAlign.CENTER)
 * button.align(HAlign.RIGHT, VAlign.CENTER)
 * panel.align(HAlign.LEFT, VAlign.TOP)
 * scrollable.align(HAlign.TRACK, VAlign.TOP)
 * ```
 */
class Align(
    child: Component,
    private val h: HAlign = HAlign.FIT,
    private val v: VAlign = VAlign.FIT,
) : JPanel(null) {

    init {
        isOpaque = false
        add(child)
    }

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------

    override fun doLayout() {
        if (componentCount == 0) return
        val child = getComponent(0)
        val ins = insets
        val availW = maxOf(0, width - ins.left - ins.right)
        val availH = maxOf(0, height - ins.top - ins.bottom)

        val min = child.minimumSize
        val max = child.maximumSize
        child.setSize(probe(h, availW, min.width, max.width), probe(v, availH, min.height, max.height))
        val pref = child.preferredSize

        val (w, cx) = placeAxis(h, availW, min.width, pref.width, max.width)
        val (ht, cy) = placeAxis(v, availH, min.height, pref.height, max.height)

        child.setBounds(ins.left + cx, ins.top + cy, w, ht)
    }

    // -----------------------------------------------------------------------
    // Wrapper size negotiation
    // -----------------------------------------------------------------------

    override fun getMinimumSize(): Dimension {
        if (componentCount == 0) return super.getMinimumSize()
        val child = getComponent(0)
        val ins = insets
        val cw = if (h == HAlign.TRACK) 0 else child.minimumSize.width
        val ch = if (v == VAlign.TRACK) 0 else child.minimumSize.height
        return Dimension(cw + ins.left + ins.right, ch + ins.top + ins.bottom)
    }

    override fun getPreferredSize(): Dimension {
        if (componentCount == 0) return super.getPreferredSize()
        val child = getComponent(0)
        val ins = insets
        val min = child.minimumSize
        val max = child.maximumSize
        val availW = maxOf(0, width - ins.left - ins.right)
        val availH = maxOf(0, height - ins.top - ins.bottom)
        if (availW > 0 || availH > 0) {
            child.setSize(
                if (availW > 0) probe(h, availW, min.width, max.width) else child.width,
                if (availH > 0) probe(v, availH, min.height, max.height) else child.height,
            )
        }
        val pref = child.preferredSize
        val cw = if (h == HAlign.TRACK) 0 else bounded(pref.width, min.width, max.width)
        val ch = if (v == VAlign.TRACK) 0 else bounded(pref.height, min.height, max.height)
        return Dimension(cw + ins.left + ins.right, ch + ins.top + ins.bottom)
    }

    override fun getMaximumSize(): Dimension {
        if (componentCount == 0) return super.getMaximumSize()
        val child = getComponent(0)
        val ins = insets
        val cw = if (h == HAlign.TRACK) super.getMaximumSize().width else maxOf(child.minimumSize.width, child.maximumSize.width) + ins.left + ins.right
        val ch = if (v == VAlign.TRACK) super.getMaximumSize().height else maxOf(child.minimumSize.height, child.maximumSize.height) + ins.top + ins.bottom
        return Dimension(cw, ch)
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Returns (size, offset) for a single axis. Offset is relative to the inner origin (after insets).
 * - TRACK: size = avail, offset = 0
 * - FIT: size = clamp(avail, min, max), offset = 0
 * - edge/center: size = clamp(boundedPref, 0, avail), offset positions according to alignment
 */
private fun placeAxis(mode: Any, avail: Int, min: Int, pref: Int, max: Int): Pair<Int, Int> {
    val effMax = maxOf(min, max)
    return when (mode) {
        HAlign.TRACK, VAlign.TRACK -> avail to 0
        HAlign.FIT, VAlign.FIT -> {
            // fill available, capped at effMax; if avail < min we still shrink to avail
            val size = minOf(avail, effMax)
            size to 0
        }
        HAlign.LEFT, VAlign.TOP -> {
            val size = minOf(bounded(pref, min, effMax), avail)
            size to 0
        }
        HAlign.CENTER, VAlign.CENTER -> {
            val size = minOf(bounded(pref, min, effMax), avail)
            size to (avail - size) / 2
        }
        HAlign.RIGHT, VAlign.BOTTOM -> {
            val size = minOf(bounded(pref, min, effMax), avail)
            size to (avail - size)
        }
        else -> avail to 0
    }
}

private fun bounded(value: Int, min: Int, max: Int) = value.coerceIn(min, maxOf(min, max))

private fun probe(mode: Any, avail: Int, min: Int, max: Int): Int {
    if (mode == HAlign.FIT || mode == VAlign.FIT) return minOf(avail, maxOf(min, max))
    return avail
}

// ---------------------------------------------------------------------------
// Factory extension
// ---------------------------------------------------------------------------

fun Component.align(h: HAlign, v: VAlign) = Align(this, h, v)
