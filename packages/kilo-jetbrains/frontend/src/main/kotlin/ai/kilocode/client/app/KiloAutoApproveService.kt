package ai.kilocode.client.app

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-level service tracking the runtime auto-approve toggle.
 *
 * This is a **client-side** toggle only. It does NOT write CLI config or
 * create persistent permission rules. While enabled, each permission request
 * is automatically replied with `"once"`.
 */
@Service(Service.Level.APP)
class KiloAutoApproveService {
    private val state = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = state.asStateFlow()

    fun active(): Boolean = state.value

    fun set(value: Boolean) {
        if (state.value == value) return
        state.value = value
    }

    fun toggle(): Boolean {
        val next = !state.value
        set(next)
        return next
    }
}
