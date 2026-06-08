package ai.kilocode.client.vfs

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import kotlinx.serialization.json.Json

class KiloVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
    fun getPath(path: KiloPath): String = json.encodeToString(KiloPath.serializer(), path)

    fun findOrCreateFile(project: Project, path: KiloPath): VirtualFile? {
        service<KiloVfsRegistry>().get(path.kind) ?: return null
        return KiloVirtualFile(project, path)
    }

    override fun findFileByPath(path: String): VirtualFile? {
        val parsed = decode(path) ?: return null
        val project = ProjectManager.getInstance().openProjects.find { it.locationHash == parsed.projectHash } ?: return null
        return findOrCreateFile(project, parsed)
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    override fun extractPresentableUrl(path: String): String {
        return (refreshAndFindFileByPath(path) as? VirtualFilePathWrapper)?.presentablePath ?: path
    }

    override fun refresh(asynchronous: Boolean) {}

    override fun getProtocol(): String = PROTOCOL

    private fun decode(path: String): KiloPath? {
        return try {
            json.decodeFromString(KiloPath.serializer(), path)
        } catch (err: Exception) {
            log.warn("Cannot deserialize $path", err)
            null
        }
    }

    companion object {
        const val PROTOCOL = "kilo"

        private val json = Json
        private val log = logger<KiloVirtualFileSystem>()
        private val fallback = KiloVirtualFileSystem()

        fun getInstance(): KiloVirtualFileSystem {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as? KiloVirtualFileSystem ?: fallback
        }
    }
}
