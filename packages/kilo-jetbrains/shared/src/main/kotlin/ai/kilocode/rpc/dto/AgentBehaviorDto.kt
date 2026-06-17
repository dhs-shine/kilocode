package ai.kilocode.rpc.dto

import kotlinx.serialization.Serializable

@Serializable
data class AgentDetailDto(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val mode: String,
    val native: Boolean? = null,
    val hidden: Boolean? = null,
    val deprecated: Boolean? = null,
    val permission: List<PermissionRuleItemDto> = emptyList(),
)

@Serializable
data class PermissionRuleItemDto(
    val tool: String,
    val pattern: String? = null,
    val action: String,
)

@Serializable
data class McpStatusDto(
    val name: String,
    val status: String,
    val error: String? = null,
)
