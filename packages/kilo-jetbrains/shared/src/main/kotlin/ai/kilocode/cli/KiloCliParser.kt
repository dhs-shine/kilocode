package ai.kilocode.cli

object KiloCliParser {
    fun tag(text: String, name: String): String? =
        Regex("<$name>\\s*([\\s\\S]*?)\\s*</$name>")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
}
