package ai.kilocode.client.vfs

import ai.kilocode.client.app.KiloWorkspaceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class KiloVfsRestoreActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val base = project.basePath ?: return
        val ws = service<KiloWorkspaceService>()
        val dir = ws.resolveProjectDirectory(base)
        val paths = ws.virtualOpenPaths(dir)
        if (paths.isEmpty()) return
        ApplicationManager.getApplication().invokeLater({
            val mgr = project.service<KiloVfsManager>()
            paths.forEach { raw ->
                val path = KiloVirtualFileSystem.decode(raw) ?: return@forEach
                mgr.openLocal(path.kind, path.params, focus = false)
            }
        }, project.disposed)
    }
}
