package misk.mcp.testing.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.Description
import misk.mcp.McpTool
import misk.mcp.createTypedElicitation

@Serializable
data class GetNicknameRequest(
  @Description(
    "This will contain the user's nickname that they want to set."
  )
  val nickname: String
)

@OptIn(ExperimentalMiskApi::class)
class NicknameElicitationTool @Inject constructor() : McpTool<ToolSchema>() {
  override val name = "nickname"
  override val description = "A test tool that says hello to the user by their nickname"

  override suspend fun handle(input: ToolSchema): ToolResult {
    val result = createTypedElicitation<GetNicknameRequest>("What nickname would you like to use?")
    val response = when (result.action){
      ElicitResult.Action.Accept -> "Hello, ${result.content?.nickname}!"
      ElicitResult.Action.Decline -> "Sorry, don't know what to call you"
      ElicitResult.Action.Cancel -> "Lets try again!"
    }
    return ToolResult(TextContent(response))
  }
}

suspend fun Client.callNicknameTool() = callTool(
  name = "nickname",
  arguments = emptyMap(),
)