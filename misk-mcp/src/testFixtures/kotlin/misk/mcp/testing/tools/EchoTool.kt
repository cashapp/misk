@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp.testing.tools

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.Description
import misk.mcp.StructuredMcpTool

@Serializable data class EchoToolInput(@Description("the name to echo back") val name: String)

@Serializable data class EchoToolOutput(@Description("greeting to respond with") val greeting: String)

abstract class AbstractEchoTool : StructuredMcpTool<EchoToolInput, EchoToolOutput>() {
  override suspend fun handle(input: EchoToolInput): ToolResult {
    return ToolResult(result = EchoToolOutput(buildGreeting(input.name)))
  }

  override val name = "EchoTool"

  open fun buildGreeting(name: String) = "Hello, $name!"
}

class EchoTool @Inject constructor() : AbstractEchoTool() {
  override val description = "This tool will return Hello, <name>!"
}

class EchoToolV2 @Inject constructor() : AbstractEchoTool() {
  override val description = "This tool will return Hello again, <name>!"

  override fun buildGreeting(name: String) = "Hello again, $name!"
}
