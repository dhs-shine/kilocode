package ai.kilocode.client.vfs

import kotlinx.serialization.Serializable

@Serializable
data class KiloPath(
    val launchId: String,
    val projectHash: String,
    val kind: String,
    val params: Map<String, String> = emptyMap(),
)
