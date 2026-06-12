package ai.kilocode.client.ui.md.hybrid

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

internal data class ShellRange(val start: Int, val end: Int, val key: TextAttributesKey)

internal data class ShellDisplay(val text: String, val ranges: List<ShellRange>)

internal object MdShellHighlight {
    private val hash = Regex("(?m)^[0-9a-f]{7,40}(?=\\s)")
    private val commit = Regex("^[0-9a-f]{7,40}(?:\\s|$)")
    private val file = Regex("^\\s+.+\\s+\\|\\s+\\d+")
    private val summary = Regex("^\\d+ files? changed(?:,|$)")
    private val refs = Regex("\\((?:HEAD|origin|main|master|develop|release|feature|bugfix|hotfix|[^)]+/[^)]+)[^)]*\\)")
    private val plus = Regex("\\+{2,}")
    private val minus = Regex("-{2,}")
    private val insertions = Regex("\\b\\d+ insertions?\\(\\+\\)")
    private val deletions = Regex("\\b\\d+ deletions?\\(-\\)")
    private val meta = Regex("(?m)^<(?:shell_metadata|/shell_metadata)>$")
    private val cut = Regex("(?m)^\\.\\.\\.output truncated\\.\\.\\.$")

    fun project(text: String): ShellDisplay {
        val out = mutableListOf<String>()
        var grouped = false
        var stat = false

        for (line in text.lines()) {
            val header = commit.containsMatchIn(line)
            if (header && grouped && stat && out.lastOrNull()?.isNotEmpty() == true) out.add("")
            out.add(line)
            if (header) grouped = true
            stat = line.isNotBlank() && (file.containsMatchIn(line) || summary.containsMatchIn(line))
        }

        val display = out.joinToString("\n")
        return ShellDisplay(display, ranges(display))
    }

    fun ranges(text: String): List<ShellRange> = buildList {
        fun add(regex: Regex, key: TextAttributesKey) {
            regex.findAll(text).forEach { match ->
                add(ShellRange(match.range.first, match.range.last + 1, key))
            }
        }

        add(hash, DefaultLanguageHighlighterColors.NUMBER)
        add(refs, DefaultLanguageHighlighterColors.KEYWORD)
        add(insertions, DefaultLanguageHighlighterColors.STRING)
        add(deletions, DefaultLanguageHighlighterColors.LINE_COMMENT)
        add(plus, DefaultLanguageHighlighterColors.STRING)
        add(minus, DefaultLanguageHighlighterColors.LINE_COMMENT)
        add(meta, DefaultLanguageHighlighterColors.DOC_COMMENT)
        add(cut, DefaultLanguageHighlighterColors.KEYWORD)
    }
}
