package misk.mcp.testing.tools

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.Description
import misk.mcp.McpToolEmptyInput
import misk.mcp.inputRequired
import misk.mcp.inputResponse
import misk.mcp.protocol.ElicitResult
import misk.mcp.protocol.TextContent
import misk.mcp.requestState

@Serializable
data class GetNicknameRequest(
  @Description("This will contain the user's nickname that they want to set.") val nickname: String
)

@Serializable data class NicknameState(val greeting: String)

/** Asks the user for a nickname, carrying the greeting to the second round in a signed requestState. */
@OptIn(ExperimentalMiskApi::class)
class NicknameElicitationTool @Inject constructor() : McpToolEmptyInput() {
  override val name = "nickname"
  override val description = "A test tool that says hello to the user by their nickname"

  override suspend fun handle(): ToolResult {
    val answer =
      inputResponse<GetNicknameRequest>("nickname")
        ?: inputRequired(NicknameState(greeting = "Hello")) {
          elicit<GetNicknameRequest>("nickname", "What nickname would you like to use?")
        }
    val text =
      when (answer.action) {
        ElicitResult.Action.Accept -> "${requestState<NicknameState>()?.greeting}, ${answer.content?.nickname}!"
        ElicitResult.Action.Decline -> "Sorry, don't know what to call you"
        ElicitResult.Action.Cancel -> "Let's try again!"
      }
    return ToolResult(TextContent(text))
  }
}
