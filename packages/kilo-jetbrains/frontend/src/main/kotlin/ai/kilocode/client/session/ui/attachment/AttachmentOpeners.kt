package ai.kilocode.client.session.ui.attachment

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.BinaryLightVirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

@RequiresEdt
fun openEmbeddedAttachment(project: Project, name: String, mime: String, url: String) {
    val data = parseDataUrl(url) ?: return
    val type = FileTypeManager.getInstance().getFileTypeByFileName(name)
    val file = if (textual(mime)) {
        LightVirtualFile(name, type, data.bytes.toString(StandardCharsets.UTF_8))
    } else {
        BinaryLightVirtualFile(name, type, data.bytes)
    }
    FileEditorManager.getInstance(project).openFile(file, true)
}

@RequiresEdt
fun openEmbeddedAttachment(project: Project, item: AttachmentCardItem) {
    openEmbeddedAttachment(project, item.name, item.mime, item.url)
}

fun decodeDataImage(url: String): ByteArray? {
    val data = parseDataUrl(url) ?: return null
    if (!data.mime.startsWith("image/")) return null
    return data.bytes
}

private data class DataUrl(val mime: String, val bytes: ByteArray)

private fun parseDataUrl(url: String): DataUrl? {
    if (!url.startsWith("data:")) return null
    val comma = url.indexOf(',')
    if (comma < 0) return null
    val meta = url.substring(5, comma)
    val body = url.substring(comma + 1)
    val parts = meta.split(';').filter { it.isNotBlank() }
    val mime = parts.firstOrNull()?.takeIf { it.contains('/') } ?: "text/plain"
    val bytes = if (parts.any { it.equals("base64", ignoreCase = true) }) {
        runCatching { Base64.getDecoder().decode(body) }.getOrNull() ?: return null
    } else {
        URLDecoder.decode(body, StandardCharsets.UTF_8).toByteArray(StandardCharsets.UTF_8)
    }
    return DataUrl(mime, bytes)
}

private fun textual(mime: String) = mime.startsWith("text/") || mime in setOf(
    "application/json",
    "application/javascript",
    "application/xml",
    "application/x-yaml",
)

fun isEmbeddedAttachment(url: String) = url.startsWith("data:")

fun isLocalAttachment(url: String) = runCatching { java.net.URI.create(url).scheme == "file" }.getOrDefault(false)
