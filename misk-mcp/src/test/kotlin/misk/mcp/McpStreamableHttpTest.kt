@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpProtocolTest.Companion.mcpTestModule
import misk.mcp.config.McpServerConfig
import misk.mcp.protocol.JSONRPCError
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.LATEST_LEGACY_PROTOCOL_VERSION
import misk.mcp.protocol.McpHeaders
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.RequestId
import misk.mcp.testing.McpTestClient
import misk.mcp.testing.McpTestResponse
import misk.mcp.testing.tools.CalculatorTool
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MiskTest(startService = true)
internal class McpStreamableHttpTest {
  @MiskTestModule val module: Module = mcpTestModule(McpToolModule.create<CalculatorTool>())

  @Inject lateinit var jettyService: JettyService

  private val url by lazy { jettyService.httpServerUrl.resolve("/mcp")!! }
  private val modern by lazy { McpTestClient(OkHttpClient(), url) }
  private val legacy by lazy { McpTestClient(OkHttpClient(), url, LATEST_LEGACY_PROTOCOL_VERSION) }

  @Test
  fun `modern headers that don't match the body are rejected`() = runBlocking {
    val mismatches =
      listOf(
        mapOf(McpHeaders.PROTOCOL_VERSION to "2025-11-25"),
        mapOf(McpHeaders.METHOD to McpMethod.TOOLS_LIST),
        mapOf(McpHeaders.METHOD to null),
        mapOf(McpHeaders.NAME to "other"),
        mapOf(McpHeaders.NAME to "=?base64?not base64?="),
      )
    for (headers in mismatches) {
      modern.send(calculatorCall(), headers).assertError(400, RPCError.ErrorCode.HEADER_MISMATCH)
    }
  }

  @Test
  fun `base64 header values are decoded`() =
    runBlocking<Unit> {
      val response = modern.send(calculatorCall(), mapOf(McpHeaders.NAME to "=?base64?Y2FsY3VsYXRvcg==?="))

      assertEquals(200, response.code)
      assertThat(response.reply).isInstanceOf(JSONRPCResponse::class.java)
    }

  @Test
  fun `invalid modern requests get 400 and unknown modern methods get 404`() = runBlocking {
    modern.send(JSONRPCRequest(id = RequestId(1), method = McpMethod.SERVER_DISCOVER)).assertError(400, -32602)
    val unsupported = McpTestClient(OkHttpClient(), url, "1900-01-01")
    unsupported
      .send(unsupported.newRequest(McpMethod.TOOLS_LIST))
      .assertError(400, RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION)
    modern.send(modern.newRequest("tools/unknown")).assertError(404, RPCError.ErrorCode.METHOD_NOT_FOUND)
    modern.send(modern.newRequest(McpMethod.PING)).assertError(404, RPCError.ErrorCode.METHOD_NOT_FOUND)
  }

  @Test
  fun `legacy requests default to 2025-03-26 and reject other versions`() = runBlocking {
    val noVersion = legacy.send(legacy.newRequest(McpMethod.TOOLS_LIST), mapOf(McpHeaders.PROTOCOL_VERSION to null))
    assertEquals(200, noVersion.code)
    assertThat(noVersion.reply).isInstanceOf(JSONRPCResponse::class.java)

    legacy
      .send(legacy.newRequest(McpMethod.TOOLS_LIST), mapOf(McpHeaders.PROTOCOL_VERSION to "1900-01-01"))
      .assertError(400, RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION)
    legacy
      .send(legacy.newRequest(McpMethod.TOOLS_LIST), mapOf(McpHeaders.PROTOCOL_VERSION to "2026-07-28"))
      .assertError(400, RPCError.ErrorCode.HEADER_MISMATCH)
    legacy.send(legacy.newRequest("tools/unknown")).assertError(200, RPCError.ErrorCode.METHOD_NOT_FOUND)
  }

  @Test
  fun `GET and DELETE are not allowed`() {
    for (request in listOf(Request.Builder().url(url).get(), Request.Builder().url(url).delete())) {
      OkHttpClient().newCall(request.header("Accept", "text/event-stream").build()).execute().use {
        assertEquals(405, it.code)
      }
    }
  }

  private fun calculatorCall() =
    modern.newRequest(
      McpMethod.TOOLS_CALL,
      buildJsonObject {
        put("name", "calculator")
        putJsonObject("arguments") {
          put("operation", "ADD")
          put("first_term", 1)
          put("second_term", 2)
        }
      },
    )

  @MiskTest(startService = true)
  class OriginTest {
    @MiskTestModule
    val module: Module =
      mcpTestModule(
        McpToolModule.create<CalculatorTool>(),
        config = McpServerConfig("1.0.0", allowed_origins = listOf("https://allowed.example")),
      )

    @Inject lateinit var jettyService: JettyService

    @Test
    fun `only allowed origins are served`() = runBlocking {
      val client = McpTestClient(OkHttpClient(), jettyService.httpServerUrl.resolve("/mcp")!!)
      val request = client.newRequest(McpMethod.TOOLS_LIST)

      client.send(request, mapOf("Origin" to "https://evil.example")).assertError(403, -32600)
      assertEquals(200, client.send(request, mapOf("Origin" to "https://allowed.example")).code)
      assertEquals(200, client.send(request).code)
    }
  }

  @MiskTest(startService = true)
  class CancellationTest {
    @MiskTestModule
    val module: Module =
      mcpTestModule(McpToolModule.create<HangingTool>(), config = McpServerConfig("1.0.0", keep_alive_interval_ms = 50))

    @Inject lateinit var jettyService: JettyService
    @Inject lateinit var recorder: Recorder

    @Test
    fun `closing the stream cancels the handler`() = runBlocking {
      val okHttpClient = OkHttpClient.Builder().callTimeout(500.milliseconds.toJavaDuration()).build()
      val client = McpTestClient(okHttpClient, jettyService.httpServerUrl.resolve("/mcp")!!)

      assertThrows<IOException> { runBlocking { client.callTool("hanging") } }

      withTimeout(5.seconds) { recorder.cancelled.await() }
    }

    @Singleton
    class Recorder @Inject constructor() {
      val cancelled = CompletableDeferred<Unit>()
    }

    class HangingTool @Inject constructor(private val recorder: Recorder) : McpToolEmptyInput() {
      override val name = "hanging"
      override val description = "Never returns"

      override suspend fun handle(): ToolResult {
        try {
          awaitCancellation()
        } finally {
          recorder.cancelled.complete(Unit)
        }
      }
    }
  }

  private companion object {
    fun McpTestResponse.assertError(status: Int, code: Int) {
      assertEquals(status, this.code, body)
      assertEquals(code, (reply as JSONRPCError).error.code, body)
    }
  }
}
