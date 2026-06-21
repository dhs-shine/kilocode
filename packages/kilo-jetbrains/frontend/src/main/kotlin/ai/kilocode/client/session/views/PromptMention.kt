package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.FileAttachment
import ai.kilocode.client.session.model.Message

data class PromptMention(
    val token: String,
    val path: String,
    val start: Int,
    val end: Int,
)

fun promptMentions(msg: Message): List<PromptMention> = msg.parts.values.mapNotNull { part ->
    if (part !is FileAttachment) return@mapNotNull null
    if (!part.mime.lowercase().startsWith("text/plain")) return@mapNotNull null
    val source = part.source ?: return@mapNotNull null
    val path = source.path?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    PromptMention(
        token = source.text.value,
        path = path,
        start = source.text.start.toInt(),
        end = source.text.end.toInt(),
    )
}

fun linkifyMentions(text: String, mentions: List<PromptMention>): String {
    if (mentions.isEmpty()) return text
    val out = StringBuilder(text)
    for (mention in mentions.sortedByDescending { it.start }) {
        val link = link(mention)
        if (mention.start >= 0 && mention.end <= out.length && mention.start < mention.end) {
            val token = out.substring(mention.start, mention.end)
            if (token == mention.token) {
                out.replace(mention.start, mention.end, link)
                continue
            }
        }
        val at = out.indexOf(mention.token)
        if (at >= 0) out.replace(at, at + mention.token.length, link)
    }
    return out.toString()
}

private fun link(mention: PromptMention): String {
    val text = mention.token.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
    val href = mention.path.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
    return "[$text]($href)"
}
