package misk.mcp.testing.resources

import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpCompletionProvider
import misk.mcp.McpResourceTemplate

@OptIn(ExperimentalMiskApi::class)
class UserProfileResource @Inject constructor() : McpResourceTemplate, McpCompletionProvider {
  override val uriTemplate = "users://{userId}/profile"
  override val name = "User Profile"
  override val description = "Profile information for a specific user"
  override val mimeType = "application/json"

  override suspend fun complete(argumentName: String, value: String, contextArguments: Map<String, String>) =
    if (argumentName == "userId") listOf("alice", "albert", "bob").filter { it.startsWith(value) } else emptyList()

  override suspend fun handler(request: ReadResourceRequest, variables: Map<String, String>): ReadResourceResult {
    val userId = variables["userId"] ?: "unknown"
    return ReadResourceResult(
      contents =
        listOf(
          TextResourceContents(
            text = """{"userId": "$userId", "name": "User $userId"}""",
            uri = request.uri,
            mimeType = mimeType,
          )
        )
    )
  }
}
