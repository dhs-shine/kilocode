package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.vfs.VirtualFile

class KiloVfsOpenTracker(private val project: Project) : FileEditorManagerListener {
    @Volatile private var closing = false

    init {
        project.messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
            override fun projectClosing(project: Project) {
                if (project === this@KiloVfsOpenTracker.project) closing = true
            }
        })
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file is KiloVirtualFile) project.service<KiloVfsManager>().sync()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!closing && file is KiloVirtualFile) project.service<KiloVfsManager>().sync()
    }
}
