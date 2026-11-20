@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp.testing.tools

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.StructuredMcpToolEmptyInput
import misk.mcp.testing.McpTestClient

@Serializable data class HelloWorldToolOutput(val greeting: String)

class HelloWorldTool @Inject constructor() : StructuredMcpToolEmptyInput<HelloWorldToolOutput>() {
  override suspend fun handle(): ToolResult {
    return ToolResult(result = HelloWorldToolOutput("Hello, world!"))
  }

  override val name = "HelloWorldTool"
  override val description: String = "This tool will return Hello, world!"
}

suspend fun McpTestClient.callHelloWorld() = callTool(name = "HelloWorldTool", arguments = mapOf())
