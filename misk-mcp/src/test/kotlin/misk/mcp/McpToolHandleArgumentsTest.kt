@file:OptIn(ExperimentalMiskApi::class, ExperimentalMcpApi::class)

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.buildCallToolRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import org.junit.jupiter.api.Test

class McpToolHandleArgumentsTest {

  @Serializable data class RequiredInput(val text: String)

  @Serializable data class DefaultedInput(val limit: Int = 10)

  @Serializable data class StatusOutput(val status: String)

  private class EchoTool : McpTool<RequiredInput>() {
    override val name = "echo"
    override val description = "Echoes required text"

    override suspend fun handle(input: RequiredInput): ToolResult = ToolResult(TextContent(input.text))
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

  private class DefaultedTool : McpTool<DefaultedInput>() {
    override val name = "list_items"
    override val description = "Uses defaulted input properties"

    override suspend fun handle(input: DefaultedInput): ToolResult = ToolResult(TextContent("limit=${input.limit}"))
  }

  @Test
  fun `handler decodes omitted arguments for McpToolEmptyInput`() = runTest {
    val result = PingTool().handler(buildCallToolRequest { name = "ping" })

    assertNotEquals(true, result.isError)
    assertEquals("pong", (result.content.single() as TextContent).text)
  }

  @Test
  fun `handler decodes omitted arguments for StructuredMcpToolEmptyInput`() = runTest {
    val result = StatusTool().handler(buildCallToolRequest { name = "status" })

    assertNotEquals(true, result.isError)
    assertEquals(StatusOutput(status = "ok"), assertNotNull(result.structuredContent).decode<StatusOutput>())
  }

  @Test
  fun `handler decodes omitted arguments when input properties have defaults`() = runTest {
    val result = DefaultedTool().handler(buildCallToolRequest { name = "list_items" })

    assertNotEquals(true, result.isError)
    assertEquals("limit=10", (result.content.single() as TextContent).text)
  }

  @Test
  fun `handler returns error result when required arguments are omitted`() = runTest {
    val result = EchoTool().handler(buildCallToolRequest { name = "echo" })

    assertEquals(true, result.isError)
  }
}
