package ai.kilocode.client.files

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest

@Serializable
data class KiloEditorFileDescriptor(
    val version: Int = VERSION,
    val kind: String,
    val title: String,
    val directory: String? = null,
    val sessionId: String? = null,
    val messageId: String? = null,
    val partId: String? = null,
    val attachmentKey: String? = null,
    val filename: String? = null,
    val mime: String? = null,
) {
    fun validate(): Boolean {
        if (version != VERSION) return false
        if (kind == SESSION_ATTACHMENT) {
            return sessionId.isPresent() &&
                messageId.isPresent() &&
                partId.isPresent() &&
                directory.isPresent()
        }
        return false
    }

    companion object {
        const val VERSION = 1
        const val SESSION_ATTACHMENT = "sessionAttachment"
        const val ATTACHMENT_EXTENSION = "kiloattachment"

        fun attachment(
            session: String,
            message: String,
            part: String,
            key: String,
            name: String,
            mime: String,
            dir: String,
        ) = KiloEditorFileDescriptor(
            kind = SESSION_ATTACHMENT,
            title = name,
            directory = dir,
            sessionId = session,
            messageId = message,
            partId = part,
            attachmentKey = key,
            filename = name,
            mime = mime,
        )
    }
}

object KiloEditorFileDescriptors {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(value: KiloEditorFileDescriptor): String = json.encodeToString(value)

    fun decode(value: String): KiloEditorFileDescriptor = json.decodeFromString(value)

    fun path(root: Path, value: KiloEditorFileDescriptor): Path {
        val dir = when (value.kind) {
            KiloEditorFileDescriptor.SESSION_ATTACHMENT -> "session-attachments"
            else -> value.kind
        }
        return root.resolve(dir).resolve(filename(value))
    }

    fun filename(value: KiloEditorFileDescriptor): String {
        if (value.kind == KiloEditorFileDescriptor.SESSION_ATTACHMENT) {
            val name = safe(value.filename ?: value.title)
            val session = short(value.sessionId.orEmpty())
            val message = short(value.messageId.orEmpty())
            val part = short(value.partId.orEmpty())
            return "attachment__${name}__ses_${session}__msg_${message}__part_${part}__${hash(encode(value))}.${KiloEditorFileDescriptor.ATTACHMENT_EXTENSION}"
        }
        return "${safe(value.title)}__${hash(encode(value))}.kilo"
    }

    fun safe(value: String): String {
        val cleaned = value.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
        return cleaned.take(48).ifBlank { "file" }
    }

    fun hash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun short(value: String): String = safe(value).take(16).ifBlank { "none" }
}

private fun String?.isPresent(): Boolean = !this.isNullOrBlank()
