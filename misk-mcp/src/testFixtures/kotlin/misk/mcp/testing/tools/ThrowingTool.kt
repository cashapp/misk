@file:Suppress("PropertyName", "LocalVariableName")

package misk.mcp.testing.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.Description
import misk.mcp.McpTool

@Serializable
data class ThrowingToolInput (
  @Description("dummy parameter, can't have empty parameter list")
  val dummy: String
)

@OptIn(ExperimentalMiskApi::class)
class ThrowingTool @Inject constructor() : McpTool<ThrowingToolInput>() {
  override val name = "throwing"
  override val description =
    """A tool that throws an exception, used for testing exception handling logic. 
      |""".trimMargin()

  override suspend fun handle(input: ThrowingToolInput): ToolResult {
    throw IllegalStateException("boom!")
  }

}

suspend fun Client.callThrowingTool() = callTool(
  name = "throwing",
  arguments = mapOf("dummy" to "dummy"),
)
