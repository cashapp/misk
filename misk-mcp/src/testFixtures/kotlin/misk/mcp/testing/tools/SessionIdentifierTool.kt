package misk.mcp.testing.tools

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.Description
import misk.mcp.StructuredMcpTool
import misk.mcp.action.McpSessionId

@Serializable
data class SessionIdentifierInput(
  val dummy: String = "unused"
)

@Serializable
data class SessionIdentifierOutput(
  @Description("The current MCP session identifier")
  val sessionId: String
)

@OptIn(ExperimentalMiskApi::class)
class SessionIdentifierTool @Inject constructor(
  private val sessionId: McpSessionId
) : StructuredMcpTool<SessionIdentifierInput, SessionIdentifierOutput>() {
  override val name = "session_identifier"
  override val description = "Returns the current MCP session ID"

  override suspend fun handle(input: SessionIdentifierInput): ToolResult {
    val currentSessionId = sessionId.get()
    return ToolResult(
      SessionIdentifierOutput(sessionId = currentSessionId)
    )
  }
}
