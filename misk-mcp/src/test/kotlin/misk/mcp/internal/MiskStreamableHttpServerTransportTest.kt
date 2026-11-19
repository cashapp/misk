package misk.mcp.internal

import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.test.runTest
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpJson
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

@OptIn(ExperimentalMiskApi::class)
class MiskStreamableHttpServerTransportTest {

  private val httpCall = mockk<HttpCall>(relaxed = true)
  private val sendChannel = mockk<SendChannel<ServerSentEvent>>(relaxed = true)

  private fun buildTransport() = MiskStreamableHttpServerTransport(httpCall, sendChannel)

  private val testRequest =
    McpJson.decodeFromString<JSONRPCRequest>(
      """
      {
        "jsonrpc": "2.0",
        "id": "test-123",
        "method": "tools/list",
        "params": {}
      }
      """
        .trimIndent()
    )

  private val testNotification =
    McpJson.decodeFromString<JSONRPCNotification>(
      """
      {
        "jsonrpc": "2.0",
        "method": "notifications/initialized",
        "params": {}
      }
      """
        .trimIndent()
    )

  private val testResponse =
    McpJson.decodeFromString<JSONRPCResponse>(
      """
      {
        "jsonrpc": "2.0",
        "id": "test-123",
        "result": {
          "tools": []
        }
      }
      """
        .trimIndent()
    )

  @Test
  fun `start() initializes transport and allows sending messages`() = runTest {
    val transport = buildTransport()

    // Verify initial state - transport should not be initialized
    assertFailsWith<IllegalStateException> { transport.send(testRequest) }

    // Call start() and verify it succeeds
    transport.start()

    // Verify that after start(), the transport is initialized by successfully sending a message
    transport.send(testRequest)

    // Verify that the message was sent to the channel
    val expectedEvent = ServerSentEvent(event = "message", data = McpJson.encodeToString(testRequest))
    coVerify { sendChannel.send(expectedEvent) }
  }

  @Test
  fun `start() throws error when called twice`() = runTest {
    val transport = buildTransport()

    transport.start()

    assertFailsWith<IllegalStateException> { transport.start() }
  }

  @Test
  fun `send() throws error when not initialized`() = runTest {
    val transport = buildTransport()

    assertFailsWith<IllegalStateException> { transport.send(testRequest) }
  }

  @Test
  fun `close() closes channel and prevents further operations`() = runTest {
    val transport = buildTransport()
    transport.start()

    // Verify we can send before closing
    transport.send(testRequest)

    // Close the transport
    transport.close()

    // Verify channel was closed
    coVerify { sendChannel.close() }

    // Verify we can't send after closing
    assertFailsWith<IllegalStateException> { transport.send(testRequest) }
  }

  @Test
  fun `close() is idempotent`() = runTest {
    val transport = buildTransport()
    transport.start()

    transport.close()
    transport.close() // Should not throw

    // Verify channel.close() was called only once
    coVerify(exactly = 1) { sendChannel.close() }
  }

  @Test
  fun `handleMessage() throws AcceptedResponseException for notifications`() = runTest {
    val transport = buildTransport()
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage {}

    assertFailsWith<AcceptedResponseException> { transport.handleMessage(testNotification) }
  }

  @Test
  fun `handleMessage() throws AcceptedResponseException for responses`() = runTest {
    val transport = buildTransport()
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage {}

    assertFailsWith<AcceptedResponseException> { transport.handleMessage(testResponse) }
  }

  @Test
  fun `handleMessage() calls onMessage callback`() = runTest {
    val transport = buildTransport()
    transport.start()

    var messageReceived: JSONRPCMessage? = null
    transport.onMessage { message -> messageReceived = message }

    transport.handleMessage(testRequest)

    assertEquals(testRequest, messageReceived)
  }

  @Test
  fun `handleMessage() calls onError callback when onMessage throws`() = runTest {
    val transport = buildTransport()
    transport.start()

    val testException = RuntimeException("Test error")
    var errorReceived: Throwable? = null

    transport.onMessage { throw testException }
    transport.onError { error -> errorReceived = error }

    assertFailsWith<RuntimeException> { transport.handleMessage(testRequest) }

    assertEquals(testException, errorReceived)
  }

  @Test
  fun `streamId is unique for each transport instance`() = runTest {
    val transport1 = buildTransport()
    val transport2 = buildTransport()

    assertTrue(transport1.streamId != transport2.streamId)
    assertTrue(transport1.streamId.isNotEmpty())
    assertTrue(transport2.streamId.isNotEmpty())
  }
}
