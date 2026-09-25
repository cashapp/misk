package misk.mcp.internal

import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpJson
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

@OptIn(ExperimentalMiskApi::class)
class MiskStreamableHttpServerTransportTest {
  private val request =
    McpJson.decodeFromString<JSONRPCRequest>("""{"jsonrpc":"2.0","id":"test-123","method":"tools/list","params":{}}""")

  @Test
  fun `HTTP request waits for asynchronously dispatched response`() = runTest {
    val channel = Channel<ServerSentEvent>(Channel.UNLIMITED)
    val transport = MiskStreamableHttpServerTransport(mockk<HttpCall>(relaxed = true), null, channel)
    val response =
      McpJson.decodeFromString<JSONRPCResponse>("""{"jsonrpc":"2.0","id":"test-123","result":{"tools":[]}}""")
    val unrelatedResponse =
      McpJson.decodeFromString<JSONRPCResponse>("""{"jsonrpc":"2.0","id":"another-request","result":{}}""")
    transport.start()
    transport.onMessage {
      transport.send(unrelatedResponse)
      launch {
        delay(10)
        transport.send(response)
      }
    }

    transport.handleMessage(request)

    assertEquals(
      ServerSentEvent(event = "message", data = McpJson.encodeToString(unrelatedResponse)),
      channel.tryReceive().getOrNull(),
    )
    assertEquals(
      ServerSentEvent(event = "message", data = McpJson.encodeToString(response)),
      channel.tryReceive().getOrNull(),
    )
    transport.close()
  }

  @Test
  fun `closing from a handler releases the HTTP request without a response`() = runTest {
    val channel = Channel<ServerSentEvent>(Channel.UNLIMITED)
    val transport = MiskStreamableHttpServerTransport(mockk<HttpCall>(relaxed = true), null, channel)
    transport.start()
    transport.onMessage { transport.close() }

    val failure =
      assertIs<CancellationException>(
        runCatching { withTimeout(100) { transport.handleMessage(request) } }.exceptionOrNull()
      )
    assertFalse(failure is TimeoutCancellationException)
  }
}
