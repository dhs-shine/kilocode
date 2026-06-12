package ai.kilocode.client.ui.md.hybrid

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key

internal data class Range(val start: Int, val end: Int, val key: Key<*>)

internal data class Term(val text: String, val ranges: List<Range>)

internal object MdTerminal {
    fun decode(text: String, stream: Stream): Term {
        val out = StringBuilder()
        val ranges = mutableListOf<Range>()
        val key = when (stream) {
            Stream.Stdout -> ProcessOutputTypes.STDOUT
            Stream.Stderr -> ProcessOutputTypes.STDERR
        }
        AnsiEscapeDecoder().escapeText(controls(text), key) { chunk, attrs ->
            val start = out.length
            out.append(chunk)
            val end = out.length
            if (start != end) ranges.add(Range(start, end, attrs))
        }
        return Term(out.toString().trimEnd('\n'), ranges)
    }

    private fun controls(text: String): String {
        val out = StringBuilder()
        val line = StringBuilder()
        val src = text.replace("\r\n", "\n")
        var idx = 0
        fun esc(): String? {
            if (src[idx] != '\u001B') return null
            if (idx + 1 >= src.length || src[idx + 1] != '[') {
                idx++
                return ""
            }
            var end = idx + 2
            while (end < src.length && src[end] !in '@'..'~') end++
            if (end >= src.length) {
                idx = src.length
                return ""
            }
            val seq = src.substring(idx, end + 1)
            idx = end + 1
            return if (seq.endsWith('m')) seq else ""
        }
        while (idx < src.length) {
            val seq = esc()
            if (seq != null) {
                line.append(seq)
                continue
            }
            when (val ch = src[idx++]) {
                '\r' -> line.clear()
                '\n' -> {
                    out.append(line).append('\n')
                    line.clear()
                }
                '\b' -> if (line.isNotEmpty()) line.deleteCharAt(line.length - 1)
                '\t' -> line.append(ch)
                else -> if (!ch.isISOControl()) line.append(ch)
            }
        }
        out.append(line)
        return out.toString()
    }
}
