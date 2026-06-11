package ai.kilocode.backend.vfs

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "KiloVfsOpenFiles", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class KiloVfsOpenStore : PersistentStateComponent<KiloVfsOpenStore.State> {
    data class State(var paths: MutableList<String> = mutableListOf())

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    @Synchronized
    fun replace(paths: List<String>) {
        state = State(paths.toMutableList())
    }

    fun paths(): List<String> = state.paths.toList()
}
