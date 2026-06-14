package ai.kilocode.client.ui.md.hybrid

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType

internal sealed class Kind {
    data class Source(val file: FileType) : Kind()
    data class Terminal(val stream: Stream, val mode: Mode) : Kind()
}

internal enum class Stream { Stdout, Stderr }

internal enum class Mode { Ansi, Shell, Command }

internal object MdLanguage {
    private val terms = mapOf(
        "ansi" to Kind.Terminal(Stream.Stdout, Mode.Ansi),
        "ansi-stdout" to Kind.Terminal(Stream.Stdout, Mode.Ansi),
        "terminal" to Kind.Terminal(Stream.Stdout, Mode.Ansi),
        "terminal-output" to Kind.Terminal(Stream.Stdout, Mode.Ansi),
        "shell-command" to Kind.Terminal(Stream.Stdout, Mode.Command),
        "shell-output" to Kind.Terminal(Stream.Stdout, Mode.Shell),
        "ansi-stderr" to Kind.Terminal(Stream.Stderr, Mode.Ansi),
        "terminal-error" to Kind.Terminal(Stream.Stderr, Mode.Ansi),
        "shell-error" to Kind.Terminal(Stream.Stderr, Mode.Ansi),
    )

    private val files = mapOf(
        "kt" to "kt",
        "kotlin" to "kt",
        "js" to "js",
        "javascript" to "js",
        "jsx" to "jsx",
        "ts" to "ts",
        "typescript" to "ts",
        "tsx" to "tsx",
        "java" to "java",
        "py" to "py",
        "python" to "py",
        "sh" to "sh",
        "bash" to "sh",
        "shell" to "sh",
        "zsh" to "sh",
        "shellscript" to "sh",
        "json" to "json",
        "xml" to "xml",
        "html" to "html",
        "css" to "css",
        "md" to "md",
        "markdown" to "md",
        "yaml" to "yaml",
        "yml" to "yaml",
        "toml" to "toml",
        "go" to "go",
        "golang" to "go",
        "rs" to "rs",
        "rust" to "rs",
        "rb" to "rb",
        "ruby" to "rb",
        "php" to "php",
        "swift" to "swift",
        "scala" to "scala",
        "sql" to "sql",
        "dockerfile" to "dockerfile",
        "docker" to "dockerfile",
        "gradle" to "gradle",
        "kts" to "kts",
        "c" to "c",
        "h" to "h",
        "cpp" to "cpp",
        "c++" to "cpp",
        "cc" to "cc",
        "cxx" to "cxx",
        "hpp" to "hpp",
        "h++" to "hpp",
        "cs" to "cs",
        "csharp" to "cs",
        "c#" to "cs",
        "fs" to "fs",
        "fsharp" to "fs",
        "f#" to "fs",
        "ps1" to "ps1",
        "powershell" to "ps1",
        "pwsh" to "ps1",
        "bat" to "bat",
        "batch" to "bat",
        "cmd" to "bat",
        "makefile" to "makefile",
        "make" to "makefile",
        "terraform" to "tf",
        "tf" to "tf",
        "hcl" to "hcl",
        "vue" to "vue",
        "svelte" to "svelte",
        "graphql" to "graphql",
        "proto" to "proto",
        "ini" to "ini",
        "properties" to "properties",
        "diff" to "diff",
        "patch" to "patch",
    )

    fun kind(lang: String?): Kind {
        val key = lang?.trim()?.split(Regex("\\s+"))?.take(2)?.joinToString(" ")?.lowercase().orEmpty()
        terms[key]?.let { return it }
        if (key == "shell script") return Kind.Source(type("sh"))
        val single = key.substringBefore(' ')
        terms[single]?.let { return it }
        val ext = files[key] ?: files[single] ?: return Kind.Source(PlainTextFileType.INSTANCE)
        return Kind.Source(type(ext))
    }

    private fun type(ext: String): FileType {
        val type = FileTypeRegistry.getInstance().getFileTypeByExtension(ext)
        if (type == UnknownFileType.INSTANCE) return PlainTextFileType.INSTANCE
        return type
    }
}
