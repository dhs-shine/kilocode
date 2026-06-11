package ai.kilocode.client.session.ui.attachment

import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.vfs.KiloEditorKind
import ai.kilocode.client.vfs.KiloVfsRegistry
import ai.kilocode.client.vfs.KiloVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

internal object AttachmentEditorKind : KiloEditorKind {
    const val ID = "attachment"

    override val id: String = ID

    override fun title(project: Project, params: Map<String, String>): String {
        return name(params)
    }

    override fun icon(project: Project, params: Map<String, String>): Icon? {
        return attachmentIcon(params["mime"].orEmpty(), name(params))
    }

    override fun presentablePath(project: Project, params: Map<String, String>): String {
        return KiloBundle.message("session.attachment.path", params["sessionId"].orEmpty(), name(params))
    }

    override fun isValid(project: Project, params: Map<String, String>): Boolean {
        if (project.isDisposed) return false
        return params["sessionId"].isPresent() &&
            params["messageId"].isPresent() &&
            params["partId"].isPresent() &&
            params["directory"].isPresent()
    }

    @RequiresEdt
    override fun createContent(project: Project, file: KiloVirtualFile, parent: Disposable): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
        }
        panel.add(center(KiloBundle.message("session.attachment.loading")), BorderLayout.CENTER)
        project.service<KiloAttachmentEditorService>().load(file.path.params, parent) { data ->
            panel.removeAll()
            panel.add(component(data), BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
        }
        return panel
    }

    private fun component(data: AttachmentData): JComponent = when (data) {
        is AttachmentData.Text -> text(data.text)
        is AttachmentData.Image -> JBScrollPane(JBLabel(ImageIcon(data.image), SwingConstants.CENTER))
        is AttachmentData.Binary -> metadata(data.name, data.mime, data.size)
        is AttachmentData.Missing -> center(KiloBundle.message("session.attachment.missing"))
        is AttachmentData.Error -> center(KiloBundle.message("session.attachment.error", data.message))
    }

    private fun text(value: String): JComponent {
        val area = JBTextArea(value).apply {
            isEditable = false
            lineWrap = false
            border = JBUI.Borders.empty(UiStyle.Gap.sm())
        }
        return JBScrollPane(area)
    }

    private fun metadata(name: String, mime: String, size: Int): JComponent {
        return Stack.vertical(gap = UiStyle.Gap.sm()).apply {
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
            next(JBLabel(KiloBundle.message("session.attachment.unsupported", name)))
            next(JBLabel(KiloBundle.message("session.attachment.mime", mime.ifBlank { "unknown" })))
            next(JBLabel(KiloBundle.message("session.attachment.size", size)))
        }
    }

    private fun center(value: String): JComponent = Centerizer(JBLabel(value), Centerizer.TYPE.BOTH)

    private fun name(params: Map<String, String>): String {
        return params["filename"]?.takeIf { it.isNotBlank() }
            ?: params["partId"]?.takeIf { it.isNotBlank() }
            ?: KiloBundle.message("session.attachment.title")
    }
}

@Service(Service.Level.PROJECT)
internal class KiloAttachmentEditorService(
    private val project: Project,
    private val cs: CoroutineScope,
) {
    fun load(params: Map<String, String>, parent: Disposable, done: (AttachmentData) -> Unit) {
        var disposed = false
        val job = cs.launch {
            val data = runCatching { fetch(params) }
                .getOrElse { AttachmentData.Error(it.message ?: it::class.java.simpleName) }
            withContext(Dispatchers.Main) {
                if (!project.isDisposed && !disposed) done(data)
            }
        }
        Disposer.register(parent) {
            disposed = true
            job.cancel()
        }
    }

    private suspend fun fetch(params: Map<String, String>): AttachmentData {
        val session = params["sessionId"] ?: return AttachmentData.Missing
        val message = params["messageId"] ?: return AttachmentData.Missing
        val part = params["partId"] ?: return AttachmentData.Missing
        val dir = params["directory"] ?: return AttachmentData.Missing
        val item = project.service<KiloSessionService>()
            .messages(session, dir)
            .firstOrNull { it.info.id == message }
            ?.parts
            ?.firstOrNull {
                if (it.type != "file") return@firstOrNull false
                val key = params["attachmentKey"]
                if (key.isPresent()) attachmentKey(it.id, it.filename.orEmpty(), it.url.orEmpty()) == key
                else it.id == part
            }
            ?: return AttachmentData.Missing
        val data = parseDataUrl(item.url.orEmpty()) ?: return AttachmentData.Missing
        val mime = item.mime?.takeIf { it.isNotBlank() } ?: data.mime
        val name = item.filename?.takeIf { it.isNotBlank() }
            ?: params["filename"]?.takeIf { it.isNotBlank() }
            ?: part
        if (textual(mime)) return AttachmentData.Text(data.bytes.toString(Charsets.UTF_8))
        if (mime.startsWith("image/")) {
            return withContext(Dispatchers.IO) {
                val image = ImageIO.read(ByteArrayInputStream(data.bytes)) ?: return@withContext AttachmentData.Binary(name, mime, data.bytes.size)
                AttachmentData.Image(image)
            }
        }
        return AttachmentData.Binary(name, mime, data.bytes.size)
    }
}

fun ensureAttachmentEditorKind() {
    service<KiloVfsRegistry>().register(AttachmentEditorKind)
}

internal fun attachmentParams(
    sessionId: String,
    messageId: String,
    item: ai.kilocode.client.session.model.FileAttachment,
    filename: String,
    directory: String,
): Map<String, String> = linkedMapOf(
    "sessionId" to sessionId,
    "messageId" to messageId,
    "partId" to item.id,
    "attachmentKey" to attachmentKey(item.id, item.filename.orEmpty(), item.url),
    "filename" to filename,
    "mime" to item.mime,
    "directory" to directory,
)

internal sealed interface AttachmentData {
    data class Text(val text: String) : AttachmentData
    data class Image(val image: BufferedImage) : AttachmentData
    data class Binary(val name: String, val mime: String, val size: Int) : AttachmentData
    data object Missing : AttachmentData
    data class Error(val message: String) : AttachmentData
}

private fun String?.isPresent(): Boolean = !this.isNullOrBlank()

private fun attachmentKey(part: String, name: String, url: String): String {
    val value = listOf(part, name, url).joinToString("\u0000")
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.take(16).joinToString("") { "%02x".format(it) }
}
