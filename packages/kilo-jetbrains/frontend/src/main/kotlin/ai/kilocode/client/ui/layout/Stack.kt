package ai.kilocode.client.ui.layout

import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel

enum class StackAxis { VERTICAL, HORIZONTAL }

/**
 * A transparent one-dimensional layout panel for rows and columns.
 *
 * Vertical stacks make every child track the available width while preserving
 * each child's bounded preferred height. Horizontal stacks do the opposite.
 * Children are probed with the known cross-axis size before preferred size is
 * read, so wrapping components can report the preferred size for that width or
 * height.
 */
open class Stack(
    private val axis: StackAxis,
    private val gap: Int = 0,
) : JPanel(null) {

    private val entries = mutableListOf<Entry>()

    init {
        isOpaque = false
    }

    fun next(child: Component): Stack {
        add(child)
        return this
    }

    fun gap(size: Int = gap): Stack {
        entries.add(Entry.Gap(size))
        revalidate()
        return this
    }

    override fun addImpl(comp: Component, constraints: Any?, index: Int) {
        super.addImpl(comp, constraints, index)
        val idx = if (index < 0) entries.size else entryIndex(index)
        entries.add(idx, Entry.Child(comp))
    }

    override fun remove(comp: Component) {
        entries.removeAll { it is Entry.Child && it.comp == comp }
        super.remove(comp)
    }

    override fun remove(index: Int) {
        val comp = getComponent(index)
        entries.removeAll { it is Entry.Child && it.comp == comp }
        super.remove(index)
    }

    override fun removeAll() {
        entries.clear()
        super.removeAll()
    }

    override fun doLayout() {
        val ins = insets
        val w = maxOf(0, width - ins.left - ins.right)
        val h = maxOf(0, height - ins.top - ins.bottom)
        var x = ins.left
        var y = ins.top
        var seen = false
        var pending = 0

        for (entry in entries) {
            when (entry) {
                is Entry.Gap -> {
                    if (seen) pending += entry.size
                }
                is Entry.Child -> {
                    if (entry.comp.isVisible) {
                        if (seen) {
                            val space = if (pending > 0) pending else gap
                            if (axis == StackAxis.VERTICAL) y += space else x += space
                        }
                        pending = 0
                        seen = true
                        if (axis == StackAxis.VERTICAL) {
                            entry.comp.setSize(w, entry.comp.height.coerceAtLeast(1))
                        } else {
                            entry.comp.setSize(entry.comp.width.coerceAtLeast(1), h)
                        }
                        val pref = entry.comp.preferredSize
                        val min = entry.comp.minimumSize
                        val max = entry.comp.maximumSize
                        val cw = if (axis == StackAxis.VERTICAL) w else bound(pref.width, min.width, max.width)
                        val ch = if (axis == StackAxis.HORIZONTAL) h else bound(pref.height, min.height, max.height)
                        entry.comp.setBounds(x, y, cw, ch)
                        if (axis == StackAxis.VERTICAL) y += ch else x += cw
                    }
                }
            }
        }
    }

    override fun getMinimumSize() = size(Size.MIN)
    override fun getPreferredSize() = size(Size.PREF)
    override fun getMaximumSize() = size(Size.MAX)

    private fun size(kind: Size): Dimension {
        val ins = insets
        var main = 0
        var cross = 0
        var seen = false
        var pending = 0

        for (entry in entries) {
            when (entry) {
                is Entry.Gap -> {
                    if (seen) pending = safe(pending, entry.size)
                }
                is Entry.Child -> {
                    if (entry.comp.isVisible) {
                        if (seen) main = safe(main, if (pending > 0) pending else gap)
                        pending = 0
                        seen = true
                        val dim = dim(entry.comp, kind, crossSize())
                        main = safe(main, if (axis == StackAxis.VERTICAL) dim.height else dim.width)
                        cross = maxOf(cross, if (axis == StackAxis.VERTICAL) dim.width else dim.height)
                    }
                }
            }
        }

        val w = if (axis == StackAxis.VERTICAL) cross else main
        val h = if (axis == StackAxis.VERTICAL) main else cross
        return Dimension(safe(w, ins.left + ins.right), safe(h, ins.top + ins.bottom))
    }

    private fun dim(comp: Component, kind: Size, cross: Int): Dimension {
        if (kind == Size.MIN) return comp.minimumSize
        val min = comp.minimumSize
        if (kind == Size.MAX) {
            val max = comp.maximumSize
            return Dimension(maxOf(min.width, max.width), maxOf(min.height, max.height))
        }
        if (cross > 0) {
            if (axis == StackAxis.VERTICAL) {
                comp.setSize(cross, comp.height.coerceAtLeast(1))
            } else {
                comp.setSize(comp.width.coerceAtLeast(1), cross)
            }
        }
        val pref = comp.preferredSize
        val max = comp.maximumSize
        return Dimension(bound(pref.width, min.width, max.width), bound(pref.height, min.height, max.height))
    }

    private fun crossSize(): Int {
        val ins = insets
        if (axis == StackAxis.VERTICAL) return maxOf(0, width - ins.left - ins.right)
        return maxOf(0, height - ins.top - ins.bottom)
    }

    private fun entryIndex(index: Int): Int {
        var count = 0
        for ((idx, entry) in entries.withIndex()) {
            if (entry is Entry.Child) {
                if (count == index) return idx
                count++
            }
        }
        return entries.size
    }

    private sealed interface Entry {
        data class Child(val comp: Component) : Entry
        data class Gap(val size: Int) : Entry
    }

    private enum class Size { MIN, PREF, MAX }

    companion object {
        fun vertical(gap: Int = 0) = Stack(StackAxis.VERTICAL, gap)
        fun horizontal(gap: Int = 0) = Stack(StackAxis.HORIZONTAL, gap)
    }
}

private fun bound(value: Int, min: Int, max: Int) = value.coerceIn(min, maxOf(min, max))

private fun safe(a: Int, b: Int): Int {
    val sum = a.toLong() + b.toLong()
    if (sum > Int.MAX_VALUE) return Int.MAX_VALUE
    if (sum < 0) return 0
    return sum.toInt()
}
