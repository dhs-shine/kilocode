package ai.kilocode.client.ui

import com.intellij.ui.JBColor
import com.intellij.util.SVGLoader
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.io.ByteArrayInputStream
import kotlin.math.ceil
import javax.swing.Icon

private const val RGB_MASK = 0x00FFFFFF

internal fun colorizedSvgIcon(
    path: String,
    owner: Class<*>,
    fillColor: Color,
    borderColor: Color = fillColor,
    fillColors: Collection<Int>,
    borderColors: Collection<Int>,
): Icon = SvgIcon(
    path = path,
    owner = owner,
    fill = fillColor,
    border = borderColor,
    fills = fillColors.map { it and RGB_MASK }.toSet(),
    borders = borderColors.map { it and RGB_MASK }.toSet(),
)

private data class Key(
    val fill: Int,
    val border: Int,
    val bright: Boolean,
    val scale: Double,
)

private class SvgIcon(
    private val path: String,
    private val owner: Class<*>,
    private val fill: Color,
    private val border: Color,
    private val fills: Set<Int>,
    private val borders: Set<Int>,
) : Icon {
    private val cache = mutableMapOf<Key, Image>()
    private val data by lazy {
        owner.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("SVG icon not found: $path")
    }
    private val size by lazy { size(data.toString(Charsets.UTF_8)) }

    override fun getIconWidth(): Int = size.first

    override fun getIconHeight(): Int = size.second

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g as? Graphics2D
        if (g2 != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        }
        g.drawImage(image(g), x, y, iconWidth, iconHeight, null)
    }

    private fun image(g: Graphics): Image {
        val scale = scale(g)
        val key = Key(fill.rgb, border.rgb, JBColor.isBright(), scale)
        return cache.getOrPut(key) {
            SVGLoader.load(ByteArrayInputStream(patch()), scale.toFloat())
        }
    }

    private fun patch(): ByteArray {
        val svg = data.toString(Charsets.UTF_8)
        val patched = ATTR.replace(svg) {
            val attr = it.groupValues[1]
            val rgb = parse(it.groupValues[2]) ?: return@replace it.value
            val color = when {
                fills.contains(rgb) -> fill
                borders.contains(rgb) -> border
                else -> return@replace it.value
            }
            "$attr=\"${hex(color)}\""
        }
        return patched.toByteArray(Charsets.UTF_8)
    }

    private fun size(svg: String): Pair<Int, Int> {
        val width = SIZE.find(svg)?.groupValues?.get(1)?.toFloatOrNull()
        val height = SIZE.find(svg)?.groupValues?.get(2)?.toFloatOrNull()
        return Pair(ceil(width ?: 16f).toInt(), ceil(height ?: 16f).toInt())
    }

    private fun scale(g: Graphics): Double {
        if (g !is Graphics2D) return 1.0
        return g.deviceConfiguration.defaultTransform.scaleX.coerceAtLeast(1.0)
    }
}

private val ATTR = Regex("""\b(fill|stroke)=["'](#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))["']""")
private val SIZE = Regex("""<svg\b[^>]*\bwidth=["']([0-9.]+)["'][^>]*\bheight=["']([0-9.]+)["']""")

private fun parse(value: String): Int? {
    return value.removePrefix("#").take(6).toIntOrNull(16)?.and(RGB_MASK)
}

private fun hex(color: Color): String {
    return "#%02X%02X%02X".format(color.red, color.green, color.blue)
}
