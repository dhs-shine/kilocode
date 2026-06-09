package ai.kilocode.client.vfs

import com.intellij.openapi.components.Service
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class KiloVfsRegistry {
    private val kinds = ConcurrentHashMap<String, KiloEditorKind>()

    fun register(kind: KiloEditorKind) {
        kinds[kind.id] = kind
    }

    fun unregister(id: String) {
        kinds.remove(id)
    }

    fun get(id: String): KiloEditorKind? = kinds[id]
}
