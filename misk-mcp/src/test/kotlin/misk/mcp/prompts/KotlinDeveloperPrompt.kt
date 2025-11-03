package misk.mcp.prompts

import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.TextContent
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
    "Description for ${request.name}",
    messages = listOf(
      PromptMessage(
        role = Role.user,
        content = TextContent("Develop a kotlin project named <name>${request.arguments?.get("Project Name")}</name>"),
      ),
    ),
  )
}
