@file:Suppress("PropertyName", "LocalVariableName")

package misk.mcp.testing.tools

import io.modelcontextprotocol.kotlin.sdk.client.Client
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.Description
import misk.mcp.StructuredMcpTool

@Serializable
data class HierarchicalToolInput(
  @Description("dummy parameter, can't have empty parameter list")
  val dummy: String
)

@Serializable
data class HierarchicalToolOutput(
  @Description("dummy output, can't have empty output list")
  val dummy: String
)

@OptIn(ExperimentalMiskApi::class)
class HierarchicalTool @Inject constructor() : AbstractTool<HierarchicalToolInput>() {
  override val name = "hierarchical"
  override val description =
    """A tool that implements StructuredMcpTool transitively instead of directly. 
      |""".trimMargin()

  override suspend fun handle(input: HierarchicalToolInput): ToolResult {
    return ToolResult(result = HierarchicalToolOutput("test"))
  }
}

@OptIn(ExperimentalMiskApi::class)
abstract class AbstractTool<I : Any> : StructuredMcpTool<I, HierarchicalToolOutput>()

suspend fun Client.callHierarchicalTool() = callTool(
  name = "hierarchical",
  arguments = mapOf("dummy" to "dummy"),
)
