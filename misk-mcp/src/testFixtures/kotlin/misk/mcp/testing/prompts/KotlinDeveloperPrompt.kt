package misk.mcp.testing.prompts

import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpPrompt

@OptIn(ExperimentalMiskApi::class)
class KotlinDeveloperPrompt @Inject constructor() : McpPrompt {
  override val name = "Kotlin Developer"
  override val description = "Develop small kotlin applications"
  override val arguments = listOf(
    PromptArgument(
      name = "Project Name",
      description = "Project name for the new project",
      required = true,
    ),
  )

  override suspend fun handler(request: GetPromptRequest) = GetPromptResult(
    description = "Description for ${request.name}",
    messages = listOf(
      PromptMessage(
        role = Role.User,
        content = TextContent("Develop a kotlin project named <name>${request.arguments?.get("Project Name")}</name>"),
      ),
    ),
  )
}
