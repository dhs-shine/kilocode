package ai.kilocode.client.files

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.UserBinaryFileType
import javax.swing.Icon

class KiloAttachmentFileType private constructor() : UserBinaryFileType() {
    override fun getName(): String = "Kilo Session Attachment"
    override fun getDescription(): String = "Kilo session attachment descriptor"
    override fun getDefaultExtension(): String = KiloEditorFileDescriptor.ATTACHMENT_EXTENSION
    override fun getIcon(): Icon = AllIcons.FileTypes.Any_type

    companion object {
        @JvmField
        val INSTANCE = KiloAttachmentFileType()
    }
}
