package ai.kilocode.client.session.ui.prompt

import ai.kilocode.rpc.dto.PartSourceDto
import ai.kilocode.rpc.dto.PartSourceTextDto
import ai.kilocode.rpc.dto.PromptPartDto
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

fun mentionFileParts(text: String, paths: Set<String>, directory: String): List<PromptPartDto> = buildList {
    paths.filter { text.contains("@$it") }.forEach { path ->
        val token = "@$path"
        val start = text.indexOf(token).takeIf { it >= 0 } ?: return@forEach
        val target = runCatching {
            val item = Path.of(path)
            if (item.isAbsolute) item else Path.of(directory).resolve(item).normalize()
        }.getOrNull() ?: return@forEach
        add(PromptPartDto(
            type = "file",
            mime = "text/plain",
            url = target.toUri().toString(),
            filename = target.fileName?.toString(),
            source = source("file", token, start, path = path),
        ))
    }
}

fun gitChangesPart(text: String, diff: String?): PromptPartDto? {
    val raw = "@git-changes"
    val start = text.indexOf(raw)
    if (start < 0) return null
    val end = start + raw.length
    if (end < text.length && !text[end].isWhitespace()) return null
    val value = diff?.takeIf { it.isNotBlank() } ?: return null
    return dataPart("git-changes.txt", value, source("resource", raw, start, uri = "git-changes"))
}

private fun dataPart(name: String, text: String, source: PartSourceDto? = null): PromptPartDto {
    val data = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
    return PromptPartDto(type = "file", mime = "text/plain", url = "data:text/plain;charset=utf-8,$data", filename = name, source = source)
}

private fun source(type: String, token: String, start: Int, path: String? = null, uri: String? = null) = PartSourceDto(
    type = type,
    text = PartSourceTextDto(value = token, start = start.toDouble(), end = (start + token.length).toDouble()),
    path = path,
    uri = uri,
    clientName = if (type == "resource") "jetbrains" else null,
)
