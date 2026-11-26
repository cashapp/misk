package misk.mcp.testing.tools

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpTool
import misk.mcp.encode
import misk.mcp.protocol.TextContent
import misk.mcp.protocol.ToolSchema

@Serializable data class VersionMetadata(val version: String)

@OptIn(ExperimentalMiskApi::class)
class KotlinSdkTool @Inject constructor() : McpTool<ToolSchema>() {
  override val name = "kotlin-sdk-tool"
  override val description = "A test tool"
  override val meta: JsonObject = VersionMetadata(version = "1.2.3").encode()

  override suspend fun handle(input: ToolSchema) = ToolResult(TextContent("Hello, world!"))
}
