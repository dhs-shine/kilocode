package ai.kilocode.client.testing

import ai.kilocode.rpc.KiloAgentBehaviorRpcApi
import ai.kilocode.rpc.dto.AgentDetailDto
import ai.kilocode.rpc.dto.CommandDto
import ai.kilocode.rpc.dto.McpStatusDto
import ai.kilocode.rpc.dto.SkillDto

class FakeAgentBehaviorRpcApi : KiloAgentBehaviorRpcApi {
    var agents = emptyList<AgentDetailDto>()
    val agentCalls = mutableListOf<String>()
    val removals = mutableListOf<String>()

    override suspend fun agents(directory: String): List<AgentDetailDto> {
        assertNotEdt("agentBehavior.agents")
        agentCalls.add(directory)
        return agents
    }

    override suspend fun skills(directory: String): List<SkillDto> {
        assertNotEdt("agentBehavior.skills")
        return emptyList()
    }

    override suspend fun removeSkill(directory: String, location: String): Boolean {
        assertNotEdt("agentBehavior.removeSkill")
        return false
    }

    override suspend fun removeAgent(directory: String, name: String): Boolean {
        assertNotEdt("agentBehavior.removeAgent")
        removals.add(name)
        agents = agents.filterNot { it.name == name }
        return true
    }

    override suspend fun commands(directory: String): List<CommandDto> {
        assertNotEdt("agentBehavior.commands")
        return emptyList()
    }

    override suspend fun mcpStatus(directory: String): List<McpStatusDto> {
        assertNotEdt("agentBehavior.mcpStatus")
        return emptyList()
    }

    override suspend fun mcpConnect(directory: String, name: String): Boolean {
        assertNotEdt("agentBehavior.mcpConnect")
        return false
    }

    override suspend fun mcpDisconnect(directory: String, name: String): Boolean {
        assertNotEdt("agentBehavior.mcpDisconnect")
        return false
    }

    override suspend fun mcpAuthenticate(directory: String, name: String): Boolean {
        assertNotEdt("agentBehavior.mcpAuthenticate")
        return false
    }

    override suspend fun claudeCodeCompat(): Boolean {
        assertNotEdt("agentBehavior.claudeCodeCompat")
        return false
    }

    override suspend fun setClaudeCodeCompat(value: Boolean): Boolean {
        assertNotEdt("agentBehavior.setClaudeCodeCompat")
        return value
    }
}
