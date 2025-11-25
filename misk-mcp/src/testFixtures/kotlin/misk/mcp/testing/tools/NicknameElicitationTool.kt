package misk.mcp.testing.tools

import io.modelcontextprotocol.kotlin.sdk.CreateElicitationResult.Action
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
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
class NicknameElicitationTool @Inject constructor() : McpTool<Tool.Input>() {
  override val name = "nickname"
  override val description = "A test tool that says hello to the user by their nickname"

  override suspend fun handle(input: Tool.Input): ToolResult {
    val result = createTypedElicitation<GetNicknameRequest>("What nickname would you like to use?")
    val response = when (result.action){
      Action.accept -> "Hello, ${result.content?.nickname}!"
      Action.decline -> "Sorry, don't know what to call you"
      Action.cancel -> "Lets try again!"
    }
    return ToolResult(TextContent(response))
  }
}

suspend fun Client.callNicknameTool() = callTool(
  name = "nickname",
  arguments = emptyMap(),
)