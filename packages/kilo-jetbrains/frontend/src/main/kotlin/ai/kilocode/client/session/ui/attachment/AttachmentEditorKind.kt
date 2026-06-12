package ai.kilocode.client.session.ui.attachment

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.files.KiloAttachmentFileType
import ai.kilocode.client.files.KiloEditorFileDescriptor
import ai.kilocode.client.files.KiloEditorFileDescriptors
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.FileAttachment
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.dto.KiloAppStatusDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.ActionLink
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
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.beans.PropertyChangeListener

class KiloAttachmentFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.fileType == KiloAttachmentFileType.INSTANCE
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return KiloAttachmentFileEditor(project, file)
    }

    override fun disposeEditor(editor: FileEditor) {
        Disposer.dispose(editor)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "KiloAttachmentEditor"
    }
}

internal class KiloAttachmentFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {
    private var disposed = false
    private val panel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(UiStyle.Gap.pad())
    }
    private val descriptor = runCatching {
        KiloEditorFileDescriptors.decode(file.contentsToByteArray().toString(Charsets.UTF_8))
    }.getOrNull()

    @RequiresEdt
    private fun init() {
        panel.add(component(AttachmentData.Connecting), BorderLayout.CENTER)
        val descriptor = descriptor
        LOG.info("kind=attachment-editor phase=create-content valid=${descriptor?.validate() == true} project=${project.name} hash=${project.locationHash} descriptor=${descriptor?.let(::brief) ?: "invalid"}")
        if (descriptor?.validate() != true) {
            panel.removeAll()
            panel.add(component(AttachmentData.Missing), BorderLayout.CENTER)
            return
        }
        project.service<KiloAttachmentEditorService>().load(descriptor, this) { data ->
            LOG.info("kind=attachment-editor phase=render data=${describe(data)} descriptor=${brief(descriptor)}")
            panel.removeAll()
            panel.add(component(data), BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
        }
    }

    init {
        init()
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = panel
    override fun getName(): String = descriptor?.filename?.takeIf { it.isNotBlank() } ?: file.presentableName
    override fun getFile(): VirtualFile = file
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = !disposed && file.isValid
    override fun dispose() { disposed = true }
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    companion object {
        private val LOG = KiloLog.create(KiloAttachmentFileEditor::class.java)
    }
}

private fun component(data: AttachmentData): JComponent = when (data) {
        is AttachmentData.Text -> text(data.text)
        is AttachmentData.Image -> JBScrollPane(JBLabel(ImageIcon(data.image), SwingConstants.CENTER))
        is AttachmentData.Binary -> metadata(data.name, data.mime, data.size)
        is AttachmentData.Missing -> center(KiloBundle.message("session.attachment.missing"))
        is AttachmentData.Error -> center(KiloBundle.message("session.attachment.error", data.message))
        AttachmentData.Connecting -> connecting()
        AttachmentData.ConnectionFailed -> failed()
    }

private fun connecting(): JComponent {
        return Stack.horizontal(gap = UiStyle.Gap.sm()).apply {
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
            next(JBLabel(AnimatedIcon.Default()))
            next(JBLabel(KiloBundle.message("session.connection.connecting")))
        }.let { Centerizer(it, Centerizer.TYPE.BOTH) }
    }

private fun failed(): JComponent {
        return Stack.horizontal(gap = UiStyle.Gap.sm()).apply {
            border = JBUI.Borders.empty(UiStyle.Gap.pad())
            next(JBLabel(KiloBundle.message("session.connection.error.app")))
            next(ActionLink(KiloBundle.message("session.connection.retry")) {
                service<KiloAppService>().retryAsync()
            })
        }.let { Centerizer(it, Centerizer.TYPE.BOTH) }
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

@Service(Service.Level.PROJECT)
internal class KiloAttachmentEditorService(
    private val project: Project,
    private val cs: CoroutineScope,
) {
    companion object {
        private val LOG = KiloLog.create(KiloAttachmentEditorService::class.java)
    }

    fun load(descriptor: KiloEditorFileDescriptor, parent: Disposable, done: (AttachmentData) -> Unit) {
        LOG.info("kind=attachment-load phase=start project=${project.name} hash=${project.locationHash} descriptor=${brief(descriptor)}")
        var disposed = false
        val job = cs.launch {
            val app = service<KiloAppService>()
            app.connect()
            while (!disposed) {
                withContext(Dispatchers.Main) {
                    if (alive(disposed)) {
                        LOG.info("kind=attachment-load phase=connecting descriptor=${brief(descriptor)}")
                        done(AttachmentData.Connecting)
                    }
                }
                val state = app.state.first { it.status == KiloAppStatusDto.READY || it.status == KiloAppStatusDto.ERROR }
                LOG.info("kind=attachment-load phase=app-state status=${state.status} descriptor=${brief(descriptor)}")
                if (state.status == KiloAppStatusDto.ERROR) {
                    withContext(Dispatchers.Main) {
                        if (alive(disposed)) {
                            LOG.info("kind=attachment-load phase=connection-failed descriptor=${brief(descriptor)}")
                            done(AttachmentData.ConnectionFailed)
                        }
                    }
                    app.state.first { it.status != KiloAppStatusDto.ERROR }
                    continue
                }
                val data = runCatching { fetch(descriptor) }
                    .getOrElse {
                        LOG.warn("kind=attachment-load phase=fetch-error descriptor=${brief(descriptor)} message=${it.message}", it)
                        AttachmentData.Error(it.message ?: it::class.java.simpleName)
                    }
                withContext(Dispatchers.Main) {
                    if (alive(disposed)) {
                        LOG.info("kind=attachment-load phase=done data=${describe(data)} descriptor=${brief(descriptor)}")
                        done(data)
                    }
                }
                return@launch
            }
        }
        Disposer.register(parent) {
            disposed = true
            LOG.info("kind=attachment-load phase=dispose descriptor=${brief(descriptor)}")
            job.cancel()
        }
    }

    private fun alive(disposed: Boolean): Boolean = !project.isDisposed && !disposed

    private suspend fun fetch(descriptor: KiloEditorFileDescriptor): AttachmentData {
        val session = descriptor.sessionId ?: run {
            LOG.info("kind=attachment-fetch result=missing reason=session descriptor=${brief(descriptor)}")
            return AttachmentData.Missing
        }
        val message = descriptor.messageId ?: run {
            LOG.info("kind=attachment-fetch result=missing reason=message descriptor=${brief(descriptor)}")
            return AttachmentData.Missing
        }
        val part = descriptor.partId ?: run {
            LOG.info("kind=attachment-fetch result=missing reason=part descriptor=${brief(descriptor)}")
            return AttachmentData.Missing
        }
        val dir = descriptor.directory ?: run {
            LOG.info("kind=attachment-fetch result=missing reason=directory descriptor=${brief(descriptor)}")
            return AttachmentData.Missing
        }
        val messages = project.service<KiloSessionService>().messages(session, dir)
        LOG.info("kind=attachment-fetch phase=messages session=$session message=$message part=$part count=${messages.size} dir=$dir")
        val msg = messages.firstOrNull { it.info.id == message } ?: run {
            LOG.info("kind=attachment-fetch result=missing reason=message-not-found session=$session message=$message available=${messages.take(8).joinToString { it.info.id }} total=${messages.size}")
            return AttachmentData.Missing
        }
        val files = msg.parts.filter { it.type == "file" }
        LOG.info(
            "kind=attachment-fetch phase=parts session=$session message=$message part=$part files=${files.size} " +
                "candidates=${files.take(8).joinToString("|") { candidate(it.id, it.filename.orEmpty(), it.mime.orEmpty(), it.url.orEmpty()) }} truncated=${files.size > 8}"
        )
        val key = descriptor.attachmentKey
        val item = files.firstOrNull {
                if (it.type != "file") return@firstOrNull false
                if (key.isPresent()) attachmentKey(it.id, it.filename.orEmpty(), it.url.orEmpty()) == key
                else it.id == part
            } ?: run {
                LOG.info("kind=attachment-fetch result=missing reason=part-not-found session=$session message=$message part=$part key=${key ?: "none"}")
                return AttachmentData.Missing
            }
        val mode = if (key.isPresent()) "attachmentKey" else "partId"
        LOG.info("kind=attachment-fetch phase=matched mode=$mode session=$session message=$message part=${item.id} name=${item.filename.orEmpty()} mime=${item.mime.orEmpty()} url=${urlInfo(item.url.orEmpty())}")
        val data = parseDataUrl(item.url.orEmpty()) ?: run {
            LOG.info("kind=attachment-fetch result=missing reason=parse-data-url session=$session message=$message part=${item.id} url=${urlInfo(item.url.orEmpty())}")
            return AttachmentData.Missing
        }
        val mime = item.mime?.takeIf { it.isNotBlank() } ?: data.mime
        val name = item.filename?.takeIf { it.isNotBlank() }
            ?: descriptor.filename?.takeIf { it.isNotBlank() }
            ?: part
        LOG.info("kind=attachment-fetch phase=parsed session=$session message=$message part=${item.id} name=$name dtoMime=${item.mime.orEmpty()} dataMime=${data.mime} mime=$mime bytes=${data.bytes.size}")
        if (textual(mime)) return AttachmentData.Text(data.bytes.toString(Charsets.UTF_8))
        if (mime.startsWith("image/")) {
            return withContext(Dispatchers.IO) {
                val image = ImageIO.read(ByteArrayInputStream(data.bytes)) ?: return@withContext AttachmentData.Binary(name, mime, data.bytes.size)
                LOG.info("kind=attachment-fetch phase=image session=$session message=$message part=${item.id} width=${image.width} height=${image.height} bytes=${data.bytes.size}")
                AttachmentData.Image(image)
            }
        }
        return AttachmentData.Binary(name, mime, data.bytes.size)
    }
}

internal fun attachmentDescriptor(
    sessionId: String,
    messageId: String,
    item: FileAttachment,
    filename: String,
    directory: String,
): KiloEditorFileDescriptor = KiloEditorFileDescriptor.attachment(
    session = sessionId,
    message = messageId,
    part = item.id,
    key = attachmentKey(item.id, item.filename.orEmpty(), item.url),
    name = filename,
    mime = item.mime,
    dir = directory,
)

internal sealed interface AttachmentData {
    data class Text(val text: String) : AttachmentData
    data class Image(val image: BufferedImage) : AttachmentData
    data class Binary(val name: String, val mime: String, val size: Int) : AttachmentData
    data object Missing : AttachmentData
    data class Error(val message: String) : AttachmentData
    data object Connecting : AttachmentData
    data object ConnectionFailed : AttachmentData
}

private fun brief(descriptor: KiloEditorFileDescriptor): String {
    return listOf(
        "sessionId=${descriptor.sessionId ?: ""}",
        "messageId=${descriptor.messageId ?: ""}",
        "partId=${descriptor.partId ?: ""}",
        "attachmentKey=${descriptor.attachmentKey ?: ""}",
        "filename=${descriptor.filename ?: ""}",
        "mime=${descriptor.mime ?: ""}",
        "directory=${descriptor.directory ?: ""}",
    ).joinToString(prefix = "{", postfix = "}")
}

private fun String?.isPresent(): Boolean = !this.isNullOrBlank()

private fun describe(data: AttachmentData): String = when (data) {
    is AttachmentData.Text -> "text chars=${data.text.length}"
    is AttachmentData.Image -> "image width=${data.image.width} height=${data.image.height}"
    is AttachmentData.Binary -> "binary name=${data.name} mime=${data.mime} bytes=${data.size}"
    is AttachmentData.Error -> "error message=${data.message}"
    AttachmentData.Missing -> "missing"
    AttachmentData.Connecting -> "connecting"
    AttachmentData.ConnectionFailed -> "connection-failed"
}

private fun candidate(part: String, name: String, mime: String, url: String): String {
    return "part=$part name=$name mime=$mime key=${attachmentKey(part, name, url)} ${urlInfo(url)}"
}

private fun urlInfo(url: String): String {
    val scheme = url.substringBefore(':', missingDelimiterValue = "none")
    return "urlScheme=$scheme urlChars=${url.length} embedded=${isEmbeddedAttachment(url)}"
}

private fun attachmentKey(part: String, name: String, url: String): String {
    val value = listOf(part, name, url).joinToString("\u0000")
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.take(16).joinToString("") { "%02x".format(it) }
}
