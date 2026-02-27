@file:OptIn(ExperimentalMiskApi::class)

package misk.web.mcp

import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpPrompt

@Singleton
class ContainerAdminPrompt @Inject constructor() : McpPrompt {
  override val name = "container_admin"
  override val description =
    "Get instructions for inspecting and debugging this Misk container. " +
      "Provides guidance on using the available tools and resources to investigate " +
      "configuration, JVM state, web actions, Guice bindings, service graph, and database metadata."

  override val arguments =
    listOf(
      PromptArgument(
        name = "focus",
        description =
          "Optional area to focus on: config, jvm, web-actions, guice, service-graph, database, or leave empty for general overview",
        required = false,
      )
    )

  override suspend fun handler(request: GetPromptRequest): GetPromptResult {
    val args = request.params.arguments ?: emptyMap()
    val focus = args["focus"]

    val focusInstruction =
      if (focus != null) {
        "\n\nThe user wants to focus on: $focus. Start by using the get_metadata tool with id=\"$focus\" " +
          "or reading the corresponding resource."
      } else {
        ""
      }

    val promptText =
      """
      |You are inspecting a Misk container. You have access to the following tools and resources:
      |
      |## Tools
      |- **get_metadata**: Query container metadata by ID. Use id="all" to get everything, or specify:
      |  - "config" - Application configuration YAML
      |  - "jvm" - JVM runtime information (VM version, uptime, system properties)
      |  - "web-actions" - All registered HTTP endpoints
      |  - "guice" - Dependency injection bindings
      |  - "service-graph" - Service dependency graph
      |  - "database-hibernate" - Database schema and query metadata
      |
      |## Resources
      |- admin://metadata/config - Application config YAML
      |- admin://metadata/jvm - JVM runtime info
      |- admin://metadata/web-actions - Web action endpoints
      |- admin://metadata/guice - Guice bindings
      |- admin://metadata/service-graph - Service graph
      |- admin://metadata/database-hibernate - Database metadata
      |
      |## Investigation Strategy
      |1. Start with get_metadata(id="all") for a broad overview, or focus on a specific area
      |2. Use the web-actions metadata to understand available API endpoints
      |3. Use the config metadata to understand the service configuration
      |4. Use the service-graph to understand service dependencies and startup order
      |5. Use the guice metadata to understand dependency injection wiring
      |6. Use the jvm metadata for runtime diagnostics (uptime, memory, system properties)
      |7. Use the database metadata to understand data models and query patterns
      |$focusInstruction
    """
        .trimMargin()

    return GetPromptResult(
      description = "Container admin inspection guide",
      messages = listOf(PromptMessage(role = Role.User, content = TextContent(text = promptText))),
    )
  }
}
