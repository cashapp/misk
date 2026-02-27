@file:OptIn(ExperimentalMiskApi::class)

package misk.web.mcp

import com.google.inject.Provider
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpTool
import misk.web.metadata.Metadata

@Singleton
class ContainerMetadataTool
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) :
  McpTool<ContainerMetadataTool.Input>() {
  override val name = "get_metadata"
  override val description =
    "Get container admin metadata by ID. Use id=\"all\" to list all available metadata, " +
      "or specify a specific ID such as \"config\", \"jvm\", \"web-actions\", \"guice\", " +
      "\"service-graph\", or \"database-hibernate\"."
  override val readOnlyHint = true
  override val destructiveHint = false
  override val openWorldHint = false

  override suspend fun handle(input: Input): ToolResult {
    val entries =
      if (input.id == "all") {
        allMetadata
      } else {
        allMetadata.filter { it.key == input.id }
      }

    if (entries.isEmpty()) {
      val availableIds = allMetadata.keys.sorted().joinToString(", ")
      return ToolResult(
        TextContent("No metadata found for id=\"${input.id}\". Available IDs: $availableIds"),
        isError = true,
      )
    }

    val result =
      entries
        .map { (key, provider) ->
          try {
            val metadata = provider.get()
            "$key:\n${metadata.prettyPrint}"
          } catch (e: Exception) {
            "$key: Error loading metadata: ${e.message}"
          }
        }
        .joinToString("\n\n---\n\n")

    return ToolResult(TextContent(result))
  }

  @Serializable data class Input(val id: String)
}
