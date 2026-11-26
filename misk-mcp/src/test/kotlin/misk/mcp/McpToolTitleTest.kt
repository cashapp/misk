@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import org.junit.jupiter.api.Test

class McpToolTitleTest {

  @Serializable data class RequiredInput(val text: String)

  @Serializable data class StatusOutput(val status: String)

  private class EchoTool : McpTool<RequiredInput>() {
    override val name = "echo"
    override val description = "Echoes required text"

    override suspend fun handle(input: RequiredInput): ToolResult = ToolResult(TextContent(input.text))
  }

  private class CustomTitleTool : McpToolEmptyInput() {
    override val name = "custom"
    override val title = "Custom Title"
    override val description = "Uses an explicit title"

    override suspend fun handle(): ToolResult = ToolResult(TextContent("ok"))
  }

  private class PingTool : McpToolEmptyInput() {
    override val name = "ping"
    override val description = "Replies pong"

    override suspend fun handle(): ToolResult = ToolResult(TextContent("pong"))
  }

  private class StatusTool : StructuredMcpToolEmptyInput<StatusOutput>() {
    override val name = "status"
    override val description = "Returns structured status"

    override suspend fun handle(): ToolResult = ToolResult(StatusOutput(status = "ok"))
  }

  @Test
  fun `title defaults to subclass name after initialization`() {
    assertEquals("echo", EchoTool().title)
    assertEquals("ping", PingTool().title)
    assertEquals("status", StatusTool().title)
  }

  @Test
  fun `title can be overridden by subclass`() {
    assertEquals("Custom Title", CustomTitleTool().title)
  }
}
