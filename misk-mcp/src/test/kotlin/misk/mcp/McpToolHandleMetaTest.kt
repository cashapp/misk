@file:OptIn(ExperimentalMiskApi::class, ExperimentalMcpApi::class)

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.buildCallToolRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import misk.annotation.ExperimentalMiskApi
import org.junit.jupiter.api.Test

class McpToolHandleMetaTest {

  @Serializable data class CapturingInput(val name: String)

  /** Tool that overrides only the meta-aware [McpTool.handle] overload and records what it receives. */
  private class CapturingTool : McpTool<CapturingInput>() {
    override val name = "capturing"
    override val description = "captures the meta passed to handle()"

    var capturedMeta: RequestMeta? = null
      private set

    var captureCount = 0
      private set

    override suspend fun handle(input: CapturingInput, meta: RequestMeta?): ToolResult {
      capturedMeta = meta
      captureCount++
      return ToolResult(TextContent("ok"))
    }
  }

  @Test
  fun `handle(input, meta) receives populated RequestMeta when request has _meta`() = runTest {
    val tool = CapturingTool()
    val request = buildCallToolRequest {
      name = "capturing"
      arguments { put("name", JsonPrimitive("alice")) }
      meta {
        progressToken("token-42")
        put("custom-key", "custom-value")
      }
    }

    tool.handler(request)

    assertEquals(1, tool.captureCount)
    val meta = assertNotNull(tool.capturedMeta, "meta should be forwarded from request")
    assertEquals("\"custom-value\"", meta.json["custom-key"]?.toString())
  }

  @Test
  fun `handle(input, meta) receives null when request has no _meta`() = runTest {
    val tool = CapturingTool()
    val request = buildCallToolRequest {
      name = "capturing"
      arguments { put("name", JsonPrimitive("alice")) }
    }

    tool.handler(request)

    assertEquals(1, tool.captureCount)
    assertNull(tool.capturedMeta)
  }
}
