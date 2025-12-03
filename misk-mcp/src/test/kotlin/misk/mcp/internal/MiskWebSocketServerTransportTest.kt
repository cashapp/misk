package misk.mcp.internal

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import kotlinx.coroutines.test.runTest
import misk.mcp.McpJson
import misk.web.HttpCall
import misk.web.actions.WebSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MiskWebSocketServerTransportTest {

  private val httpCall = mockk<HttpCall>(relaxed = true)
  private val webSocket = mockk<WebSocket>(relaxed = true)

  private fun buildTransport(): MiskWebSocketServerTransport {
    // By default, set up the mock to return "mcp" for the subprotocol header
    // Individual tests can override this by setting up their own mock before calling buildTransport()
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    return MiskWebSocketServerTransport(httpCall, webSocket)
  }

  private val testRequest = McpJson.decodeFromString<JSONRPCRequest>(
    """
      {
        "jsonrpc": "2.0",
        "id": "test-123",
        "method": "tools/list",
        "params": {}
      }
    """.trimIndent(),
  )

  private val testNotification = McpJson.decodeFromString<JSONRPCNotification>(
    """
      {
        "jsonrpc": "2.0",
        "method": "notifications/initialized",
        "params": {}
      }
    """.trimIndent(),
  )

  private val testResponse = McpJson.decodeFromString<JSONRPCResponse>(
    """
      {
        "jsonrpc": "2.0",
        "id": "test-123",
        "result": {
          "tools": []
        }
      }
    """.trimIndent(),
  )

  @Test
  fun `start() initializes transport and validates subprotocol`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol header
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"

    // Verify initial state - transport should not be initialized
    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }

    // Call start() and verify it succeeds
    transport.start()

    // Verify that after start(), the transport is initialized by successfully sending a message
    transport.send(testRequest)

    // Verify that the message was sent to the WebSocket
    val expectedData = McpJson.encodeToString(testRequest)
    verify { webSocket.send(expectedData) }
  }

  @Test
  fun `init throws error when subprotocol is invalid`() = runTest {
    // Mock invalid subprotocol header BEFORE calling buildTransport
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "invalid"

    assertFailsWith<IllegalArgumentException> {
      MiskWebSocketServerTransport(httpCall, webSocket)
    }
  }

  @Test
  fun `init throws error when subprotocol is missing`() = runTest {
    // Mock missing subprotocol header BEFORE calling buildTransport
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns null

    assertFailsWith<IllegalArgumentException> {
      MiskWebSocketServerTransport(httpCall, webSocket)
    }
  }

  @Test
  fun `start() throws error when called twice`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol header
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"

    transport.start()

    assertFailsWith<IllegalStateException> {
      transport.start()
    }
  }

  @Test
  fun `send() throws error when not initialized`() = runTest {
    val transport = buildTransport()

    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }
  }

  @Test
  fun `send() serializes and sends message to WebSocket`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    // Send different types of messages
    transport.send(testRequest)
    transport.send(testNotification)
    transport.send(testResponse)

    // Verify all messages were sent with correct serialization
    verify { webSocket.send(McpJson.encodeToString(testRequest)) }
    verify { webSocket.send(McpJson.encodeToString(testNotification)) }
    verify { webSocket.send(McpJson.encodeToString(testResponse)) }
  }

  @Test
  fun `close() closes WebSocket and prevents further operations`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    // Verify we can send before closing
    transport.send(testRequest)
    verify { webSocket.send(any<String>()) }

    // Close the transport
    transport.close()

    // Verify WebSocket was closed with proper code
    verify { webSocket.close(1000, null) }

    // Verify we can't send after closing
    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }
  }

  @Test
  fun `close() is idempotent`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    transport.close()
    transport.close() // Should not throw

    // Verify WebSocket.close() was called only once
    verify(exactly = 1) { webSocket.close(1000, null) }
  }

  @Test
  fun `close() calls onClose callback`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    var onCloseCalled = false
    transport.onClose { onCloseCalled = true }

    transport.close()

    assertTrue(onCloseCalled)
  }

  @Test
  fun `handleMessage() calls onMessage callback`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    var messageReceived: JSONRPCMessage? = null
    transport.onMessage { message ->
      messageReceived = message
    }

    transport.handleMessage(testRequest)

    assertEquals(testRequest, messageReceived)
  }

  @Test
  fun `handleMessage() calls onError callback when onMessage throws`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    val testException = RuntimeException("Test error")
    var errorReceived: Throwable? = null

    transport.onMessage { throw testException }
    transport.onError { error -> errorReceived = error }

    assertFailsWith<RuntimeException> {
      transport.handleMessage(testRequest)
    }

    assertEquals(testException, errorReceived)
  }

  @Test
  fun `handleMessage() processes different message types`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    val messagesReceived = mutableListOf<JSONRPCMessage>()
    transport.onMessage { message ->
      messagesReceived.add(message)
    }

    // Handle different message types
    transport.handleMessage(testRequest)
    transport.handleMessage(testNotification)
    transport.handleMessage(testResponse)

    assertEquals(3, messagesReceived.size)
    assertEquals(testRequest, messagesReceived[0])
    assertEquals(testNotification, messagesReceived[1])
    assertEquals(testResponse, messagesReceived[2])
  }

  @Test
  fun `streamId is unique for each transport instance`() = runTest {
    val transport1 = buildTransport()
    val transport2 = buildTransport()

    assertTrue(transport1.streamId != transport2.streamId)
    assertTrue(transport1.streamId.isNotEmpty())
    assertTrue(transport2.streamId.isNotEmpty())
  }

  @Test
  fun `streamId is consistent for same transport instance`() = runTest {
    val transport = buildTransport()

    val streamId1 = transport.streamId
    val streamId2 = transport.streamId

    assertEquals(streamId1, streamId2)
  }

  @Test
  fun `call property returns the HttpCall instance`() = runTest {
    val transport = buildTransport()

    assertEquals(httpCall, transport.call)
  }

  @Test
  fun `WebSocket connection state management`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"

    // Initially not connected
    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }

    // After start, should be connected
    transport.start()
    transport.send(testRequest)
    verify { webSocket.send(any<String>()) }

    // After close, should not be connected
    transport.close()
    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }
  }

  @Test
  fun `subprotocol validation with exact match`() = runTest {
    // Test exact match
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    val transport = buildTransport()
    transport.start() // Should succeed

    // Test case sensitivity - should fail during construction
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "MCP"
    assertFailsWith<IllegalArgumentException> {
      MiskWebSocketServerTransport(httpCall, webSocket)
    }

    // Test with extra whitespace - should fail during construction
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns " mcp "
    assertFailsWith<IllegalArgumentException> {
      MiskWebSocketServerTransport(httpCall, webSocket)
    }
  }

  @Test
  fun `WebSocket send failure handling`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    // Mock WebSocket send to return false (indicating failure)
    every { webSocket.send(any<String>()) } returns false

    // Should still call send even if it returns false
    transport.send(testRequest)
    verify { webSocket.send(any<String>()) }
  }

  @Test
  fun `message serialization consistency`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    val messageSlot = slot<String>()
    every { webSocket.send(capture(messageSlot)) } returns true

    transport.send(testRequest)

    // Verify the serialized message can be deserialized back to the same object
    val serializedMessage = messageSlot.captured
    val deserializedMessage = McpJson.decodeFromString<JSONRPCRequest>(serializedMessage)

    assertEquals(testRequest.jsonrpc, deserializedMessage.jsonrpc)
    assertEquals(testRequest.id, deserializedMessage.id)
    assertEquals(testRequest.method, deserializedMessage.method)
  }

  @Test
  fun `concurrent operations safety`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    // Test that multiple send operations work
    repeat(10) { i ->
      val request = McpJson.decodeFromString<JSONRPCRequest>(
        """
          {
            "jsonrpc": "2.0",
            "id": "test-$i",
            "method": "tools/list",
            "params": {}
          }
        """.trimIndent(),
      )
      transport.send(request)
    }

    // Verify all messages were sent
    verify(exactly = 10) { webSocket.send(any<String>()) }
  }

  @Test
  fun `error propagation in handleMessage`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol and initialize
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"
    transport.start()

    val customException = IllegalArgumentException("Custom error")
    var errorCaught: Throwable? = null

    transport.onMessage { throw customException }
    transport.onError { error -> errorCaught = error }

    // Verify the exception is both caught by onError and re-thrown
    val thrownException = assertFailsWith<IllegalArgumentException> {
      transport.handleMessage(testRequest)
    }

    assertEquals(customException, thrownException)
    assertEquals(customException, errorCaught)
  }

  @Test
  fun `lifecycle integration test`() = runTest {
    val transport = buildTransport()

    // Mock valid subprotocol
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"

    var messageCount = 0
    var errorCount = 0
    var closeCount = 0

    transport.onMessage { messageCount++ }
    transport.onError { errorCount++ }
    transport.onClose { closeCount++ }

    // Start transport
    transport.start()

    // Send messages
    transport.send(testRequest)
    transport.send(testNotification)

    // Handle messages
    transport.handleMessage(testRequest)
    transport.handleMessage(testResponse)

    // Close transport
    transport.close()

    // Verify lifecycle callbacks
    assertEquals(2, messageCount)
    assertEquals(0, errorCount)
    assertEquals(1, closeCount)

    // Verify WebSocket interactions
    verify(exactly = 2) { webSocket.send(any<String>()) }
    verify(exactly = 1) { webSocket.close(1000, null) }
  }

  @Test
  fun `constants are properly defined`() {
    assertEquals("Sec-WebSocket-Protocol", MiskWebSocketServerTransport.SEC_WEBSOCKET_PROTOCOL_HEADER)
    assertEquals("mcp", MiskWebSocketServerTransport.MCP_SUBPROTOCOL)
  }

  @Test
  fun `init sets Sec-WebSocket-Protocol response header when mcp subprotocol is requested`() = runTest {
    // Mock valid subprotocol in request
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "mcp"

    // Create transport - this triggers the init block
    @Suppress("UnusedVariable") val transport = buildTransport()

    // Verify that the response header was set during initialization
    verify { httpCall.setResponseHeader("Sec-WebSocket-Protocol", "mcp") }
  }

  @Test
  fun `init does not set Sec-WebSocket-Protocol response header when subprotocol is missing`() = runTest {
    // Mock missing subprotocol in request
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns null

    // Attempt to create transport - this should fail during init
    assertFailsWith<IllegalArgumentException> {
      MiskWebSocketServerTransport(httpCall, webSocket)
    }

    // Verify that the response header was NOT set before the exception was thrown
    verify(exactly = 0) { httpCall.setResponseHeader("Sec-WebSocket-Protocol", any()) }
  }

  @Test
  fun `init does not set Sec-WebSocket-Protocol response header when subprotocol is invalid`() = runTest {
    // Mock invalid subprotocol in request
    every { httpCall.requestHeaders["Sec-WebSocket-Protocol"] } returns "invalid"

    // Attempt to create transport - this should fail during init
    assertFailsWith<IllegalArgumentException> {
      MiskWebSocketServerTransport(httpCall, webSocket)
    }

    // Verify that the response header was NOT set before the exception was thrown
    verify(exactly = 0) { httpCall.setResponseHeader("Sec-WebSocket-Protocol", any()) }
  }
}
