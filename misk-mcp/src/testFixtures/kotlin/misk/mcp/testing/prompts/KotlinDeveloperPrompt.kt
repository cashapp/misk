package misk.mcp.testing.prompts

import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpPrompt
import misk.mcp.protocol.GetPromptRequest
import misk.mcp.protocol.GetPromptResult
import misk.mcp.protocol.PromptArgument
import misk.mcp.protocol.PromptMessage
import misk.mcp.protocol.Role
import misk.mcp.protocol.TextContent

@OptIn(ExperimentalMiskApi::class)
class KotlinDeveloperPrompt @Inject constructor() : McpPrompt {
  override val name = "Kotlin Developer"
  override val description = "Develop small kotlin applications"
  override val arguments =
    listOf(PromptArgument(name = "Project Name", description = "Project name for the new project", required = true))

  override suspend fun handler(request: GetPromptRequest) =
    GetPromptResult(
      description = "Description for ${request.name}",
      messages =
        listOf(
          PromptMessage(
            role = Role.User,
            content =
              TextContent("Develop a kotlin project named <name>${request.arguments?.get("Project Name")}</name>"),
          )
        ),
    )
}
