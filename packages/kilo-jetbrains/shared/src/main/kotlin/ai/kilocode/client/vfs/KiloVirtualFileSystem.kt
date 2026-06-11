package ai.kilocode.client.vfs

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json

class KiloVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
    fun getPath(path: KiloPath): String = json.encodeToString(KiloPath.serializer(), path.canonical())

    fun findOrCreateFile(project: Project, path: KiloPath): VirtualFile = KiloVirtualFile(project, path.canonical())

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

    companion object {
        const val PROTOCOL = "kilo"

        private val json = Json
        private val log = logger<KiloVirtualFileSystem>()
        private val fallback = KiloVirtualFileSystem()

        fun getInstance(): KiloVirtualFileSystem {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as? KiloVirtualFileSystem ?: fallback
        }

        fun decode(path: String): KiloPath? {
            return try {
                val raw = raw(path) ?: return null
                json.decodeFromString(KiloPath.serializer(), raw).canonical()
            } catch (err: Exception) {
                log.warn("Cannot deserialize $path", err)
                null
            }
        }

        private fun raw(path: String): String? {
            if (path.startsWith("{")) return path
            if (!path.startsWith("$PROTOCOL://")) return null
            val raw = path.substringAfter("://")
            if (raw.startsWith("{")) return raw
            if (!raw.startsWith("%7B", ignoreCase = true)) return null
            return URLDecoder.decode(raw, StandardCharsets.UTF_8)
        }
    }
}
