package ai.kilocode.client.ui

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon

private const val OPAQUE_ALPHA = 255
private const val RGB_MASK = 0x00FFFFFF

internal fun Icon.colorizeIfPossible(
    fillColor: Color,
    borderColor: Color = fillColor,
    fillColors: Collection<Int>,
    borderColors: Collection<Int>,
): Icon = ColorizedIcon(
    source = this,
    fill = fillColor,
    border = borderColor,
    fills = fillColors.map { it and RGB_MASK }.toSet(),
    borders = borderColors.map { it and RGB_MASK }.toSet(),
)

private data class Key(
    val width: Int,
    val height: Int,
    val fill: Int,
    val border: Int,
    val bright: Boolean,
)

private class ColorizedIcon(
    private val source: Icon,
    private val fill: Color,
    private val border: Color,
    private val fills: Set<Int>,
    private val borders: Set<Int>,
) : Icon {
    private val cache = mutableMapOf<Key, BufferedImage>()

    override fun getIconWidth(): Int = source.iconWidth

    override fun getIconHeight(): Int = source.iconHeight

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val img = image(c)
        if (img == null) return
        g.drawImage(img, x, y, null)
    }

    private fun image(c: Component?): BufferedImage? {
        val width = iconWidth
        val height = iconHeight
        if (width <= 0 || height <= 0) return null

        val key = Key(width, height, fill.rgb, border.rgb, JBColor.isBright())
        return cache.getOrPut(key) {
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            try {
                source.paintIcon(c, g, 0, 0)
            } finally {
                g.dispose()
            }

            for (py in 0 until height) {
                for (px in 0 until width) {
                    val argb = img.getRGB(px, py)
                    val rgb = argb and RGB_MASK
                    if (fills.contains(rgb)) img.setRGB(px, py, replace(argb, fill))
                    if (borders.contains(rgb)) img.setRGB(px, py, replace(argb, border))
                }
            }

            img
        }
    }

    private fun replace(argb: Int, color: Color): Int {
        val alpha = argb ushr 24
        val mixed = alpha * color.alpha / OPAQUE_ALPHA
        return (mixed shl 24) or (color.rgb and RGB_MASK)
    }
}
