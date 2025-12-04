package misk.mcp.internal

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.test.runTest
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.BadRequestException
import misk.exceptions.NotFoundException
import misk.mcp.testing.InMemoryMcpSessionHandler
import misk.mcp.McpJson
import misk.mcp.McpSessionHandler
import misk.mcp.action.SESSION_ID_HEADER
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalMiskApi::class)
class MiskStreamableHttpServerTransportTest {

  private val httpCall = mockk<HttpCall>(relaxed = true)
  private val mcpSessionHandler = InMemoryMcpSessionHandler()
  private val sendChannel = mockk<SendChannel<ServerSentEvent>>(relaxed = true)

  private fun buildTransport(sessionHandler: McpSessionHandler? = null) =
    MiskStreamableHttpServerTransport(httpCall, sessionHandler, sendChannel)

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

  private val initializeRequest = McpJson.decodeFromString<JSONRPCRequest>(
    """
      {
        "jsonrpc": "2.0",
        "id": "init-123",
        "method": "initialize",
        "params": {
          "protocolVersion": "2024-11-05",
          "capabilities": {},
          "clientInfo": {
            "name": "test-client",
            "version": "1.0.0"
          }
        }
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
  fun `start() initializes transport and allows sending messages`() = runTest {
    val transport = buildTransport()

    // Verify initial state - transport should not be initialized
    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }

    // Call start() and verify it succeeds
    transport.start()

    // Verify that after start(), the transport is initialized by successfully sending a message
    transport.send(testRequest)

    // Verify that the message was sent to the channel
    val expectedEvent = ServerSentEvent(
      event = "message",
      data = McpJson.encodeToString(testRequest)
    )
    coVerify { sendChannel.send(expectedEvent) }
  }

  @Test
  fun `start() throws error when called twice`() = runTest {
    val transport = buildTransport()

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
    assertFailsWith<IllegalStateException> {
      transport.send(testRequest)
    }
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
  fun `handleMessage() processes initialize request with session handler`() = runTest {
    val transport = buildTransport(sessionHandler = mcpSessionHandler)
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    val responseHeaderSlot = slot<String>()
    every { httpCall.setResponseHeader(SESSION_ID_HEADER, capture(responseHeaderSlot)) } returns Unit

    transport.handleMessage(initializeRequest)

    // Verify session ID was set in response header
    assertTrue(responseHeaderSlot.isCaptured)
    val sessionId = responseHeaderSlot.captured
    assertNotNull(sessionId)
    assertTrue(sessionId.isNotEmpty())

    // Verify session is active
    assertTrue(mcpSessionHandler.isActive(sessionId))
  }

  @Test
  fun `handleMessage() validates session ID for non-initialize requests`() = runTest {
    val transport = buildTransport(sessionHandler = mcpSessionHandler)
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    // First initialize a session
    val responseHeaderSlot = slot<String>()
    every { httpCall.setResponseHeader(SESSION_ID_HEADER, capture(responseHeaderSlot)) } returns Unit
    transport.handleMessage(initializeRequest)

    // Now test with the valid session ID that was created
    val sessionId = responseHeaderSlot.captured
    every { httpCall.requestHeaders[SESSION_ID_HEADER] } returns sessionId

    // This should not throw
    transport.handleMessage(testRequest)
  }

  @Test
  fun `handleMessage() throws BadRequestException when session ID header is missing`() = runTest {
    val transport = buildTransport(sessionHandler = mcpSessionHandler)
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    every { httpCall.requestHeaders[SESSION_ID_HEADER] } returns null

    assertFailsWith<BadRequestException> {
      transport.handleMessage(testRequest)
    }
  }

  @Test
  fun `handleMessage() throws NotFoundException when session is not active`() = runTest {
    val transport = buildTransport(sessionHandler = mcpSessionHandler)
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    every { httpCall.requestHeaders[SESSION_ID_HEADER] } returns "invalid-session-id"

    assertFailsWith<NotFoundException> {
      transport.handleMessage(testRequest)
    }
  }

  @Test
  fun `handleMessage() works without session handler`() = runTest {
    val transport = buildTransport()
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    // Should not throw even without session handler
    transport.handleMessage(testRequest)
  }

  @Test
  fun `handleMessage() throws AcceptedResponseException for notifications`() = runTest {
    val transport = buildTransport()
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    assertFailsWith<AcceptedResponseException> {
      transport.handleMessage(testNotification)
    }
  }

  @Test
  fun `handleMessage() throws AcceptedResponseException for responses`() = runTest {
    val transport = buildTransport()
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    assertFailsWith<AcceptedResponseException> {
      transport.handleMessage(testResponse)
    }
  }

  @Test
  fun `handleMessage() calls onMessage callback`() = runTest {
    val transport = buildTransport()
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
  fun `streamId is unique for each transport instance`() = runTest {
    val transport1 = buildTransport()
    val transport2 = buildTransport()

    assertTrue(transport1.streamId != transport2.streamId)
    assertTrue(transport1.streamId.isNotEmpty())
    assertTrue(transport2.streamId.isNotEmpty())
  }

  @Test
  fun `session lifecycle integration test`() = runTest {
    val transport = buildTransport(sessionHandler = mcpSessionHandler)
    transport.start()

    // Set up noop onMessage callback
    transport.onMessage { }

    // Initialize session
    val responseHeaderSlot = slot<String>()
    every { httpCall.setResponseHeader(SESSION_ID_HEADER, capture(responseHeaderSlot)) } returns Unit
    transport.handleMessage(initializeRequest)

    val sessionId = responseHeaderSlot.captured
    assertTrue(mcpSessionHandler.isActive(sessionId))

    // Use session for subsequent requests
    every { httpCall.requestHeaders[SESSION_ID_HEADER] } returns sessionId
    transport.handleMessage(testRequest)

    // Terminate session
    mcpSessionHandler.terminate(sessionId)

    // Verify session is no longer active
    assertFailsWith<NotFoundException> {
      transport.handleMessage(testRequest)
    }
  }

  @Test
  fun `multiple sessions can be active simultaneously`() = runTest {
    val transport1 = buildTransport(sessionHandler = mcpSessionHandler)
    val transport2 = buildTransport(sessionHandler = mcpSessionHandler)

    transport1.start()
    transport2.start()

    // Set up onMessage callbacks to prevent hanging
    transport1.onMessage { }
    transport2.onMessage { }

    // Initialize first session
    val responseHeaderSlot1 = slot<String>()
    every { httpCall.setResponseHeader(SESSION_ID_HEADER, capture(responseHeaderSlot1)) } returns Unit
    transport1.handleMessage(initializeRequest)
    val sessionId1 = responseHeaderSlot1.captured

    // Initialize second session
    val responseHeaderSlot2 = slot<String>()
    every { httpCall.setResponseHeader(SESSION_ID_HEADER, capture(responseHeaderSlot2)) } returns Unit
    transport2.handleMessage(initializeRequest)
    val sessionId2 = responseHeaderSlot2.captured

    // Verify both sessions are active and different
    assertTrue(sessionId1 != sessionId2)
    assertTrue(mcpSessionHandler.isActive(sessionId1))
    assertTrue(mcpSessionHandler.isActive(sessionId2))
  }
}
