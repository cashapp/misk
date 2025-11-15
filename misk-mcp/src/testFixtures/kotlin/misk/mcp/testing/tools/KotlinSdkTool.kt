package misk.mcp.testing.tools

import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpTool
import misk.mcp.encode

@Serializable
data class VersionMetadata(
  val version: String,
)

@OptIn(ExperimentalMiskApi::class)
class KotlinSdkTool @Inject constructor() : McpTool<Tool.Input>() {
  override val name = "kotlin-sdk-tool"
  override val description = "A test tool"
  override val _meta: JsonObject = VersionMetadata(version = "1.2.3").encode()

  override suspend fun handle(input: Tool.Input) = ToolResult(TextContent("Hello, world!"))
}
