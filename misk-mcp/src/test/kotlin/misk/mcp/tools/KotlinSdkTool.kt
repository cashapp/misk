package misk.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpTool

@OptIn(ExperimentalMiskApi::class)
class KotlinSdkTool @Inject constructor() : McpTool<Tool.Input>() {
  override val name = "kotlin-sdk-tool"
  override val description = "A test tool"

  override suspend fun handle(input: Tool.Input) = ToolResult(TextContent("Hello, world!"))
}
