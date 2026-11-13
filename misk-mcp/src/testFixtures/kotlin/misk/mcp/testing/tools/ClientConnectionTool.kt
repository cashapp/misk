@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp.testing.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.StructuredMcpToolEmptyInput
import misk.mcp.action.currentClientConnection

@Serializable
data class ClientConnectionToolOutput(
  val sessionId: String,
)

/**
 * Test tool that verifies [currentClientConnection] is accessible from within a tool handler.
 *
 * Calls [currentClientConnection] to obtain the [ClientConnection] and returns its session ID
 * to prove the connection is available in the handler context.
 */
class ClientConnectionTool @Inject constructor() : StructuredMcpToolEmptyInput<ClientConnectionToolOutput>() {
  override val name = "client_connection"
  override val description: String = "Returns client connection info including session ID"

  override suspend fun handle(): ToolResult {
    val connection = currentClientConnection()
    return ToolResult(
      result = ClientConnectionToolOutput(
        sessionId = connection.sessionId,
      )
    )
  }
}

suspend fun Client.callClientConnectionTool() =
  callTool(
    name = "client_connection",
    arguments = mapOf(),
  )
