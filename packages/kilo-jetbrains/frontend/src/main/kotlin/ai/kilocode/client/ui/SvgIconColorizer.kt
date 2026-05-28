package ai.kilocode.client.ui

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
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
)

private class SvgIcon(
    private val path: String,
    private val owner: Class<*>,
    private val fill: Color,
    private val border: Color,
    private val fills: Set<Int>,
    private val borders: Set<Int>,
) : Icon {
    private val cache = mutableMapOf<Key, Icon>()
    private val data by lazy {
        owner.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("SVG icon not found: $path")
    }

    override fun getIconWidth(): Int = icon().iconWidth

    override fun getIconHeight(): Int = icon().iconHeight

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) = icon().paintIcon(c, g, x, y)

    private fun icon(): Icon {
        val key = Key(fill.rgb, border.rgb, JBColor.isBright())
        return cache.getOrPut(key) {
            IconLoader.findIcon(url(patch()), false) ?: IconLoader.getIcon(path, owner)
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

    private fun url(data: ByteArray): URL {
        val name = path.substringAfterLast('/').removeSuffix(".svg")
        val key = "${name}-${fill.rgb}-${border.rgb}-${JBColor.isBright()}.svg"
        return URL.of(URI("memory:/kilo-icons/$key"), Handler(data))
    }
}

private class Handler(private val data: ByteArray) : URLStreamHandler() {
    override fun openConnection(u: URL): URLConnection {
        return object : URLConnection(u) {
            override fun connect() {}

            override fun getInputStream() = ByteArrayInputStream(data)
        }
    }
}

private val ATTR = Regex("""\b(fill|stroke)=["'](#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))["']""")

private fun parse(value: String): Int? {
    return value.removePrefix("#").take(6).toIntOrNull(16)?.and(RGB_MASK)
}

private fun hex(color: Color): String {
    return "#%02X%02X%02X".format(color.red, color.green, color.blue)
}
