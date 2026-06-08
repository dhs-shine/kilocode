package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.PromptPartDto
import java.nio.file.Path
import kotlin.io.path.name

data class PromptAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val url: String,
    val path: Path? = null,
) {
    fun part() = PromptPartDto(
        type = "file",
        mime = mime,
        url = url,
        filename = name,
    )
}

object PromptAttachmentExtractor {
    fun files(files: List<java.io.File>): List<PromptAttachment> = files
        .filter { it.exists() }
        .map { file ->
            val path = file.toPath()
            PromptAttachment(
                id = path.toAbsolutePath().normalize().toString(),
                name = path.fileName?.toString() ?: path.name,
                mime = mime(file),
                url = path.toUri().toString(),
                path = path,
            )
        }

    fun media(mime: String): Boolean = mime.startsWith("image/") || mime == "application/pdf"

    private fun mime(file: java.io.File): String {
        if (file.isDirectory) return "application/x-directory"
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt", "md", "kt", "kts", "java", "js", "jsx", "ts", "tsx", "json", "xml", "html", "css", "scss", "yml", "yaml", "toml", "sh", "py", "rb", "go", "rs", "c", "cc", "cpp", "h", "hpp" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
