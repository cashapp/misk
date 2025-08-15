package misk.mcp.internal

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import kotlinx.coroutines.test.runTest
import misk.web.sse.ServerSentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MiskServerTransportTest {

  private val mockSession = mockk<MiskSseServerSession> {
    every { sessionId } returns "test-session-123"
    coEvery { send(any<ServerSentEvent>()) } returns Unit
    coEvery { send(any(), any(), any(), any(), any()) } returns Unit
    every { close() } returns Unit
  }

  private val transport = MiskServerTransport(mockSession)

  private val testMessage = McpJson.decodeFromString<JSONRPCMessage>(
    """
      {
        "jsonrpc": "2.0",
        "id": "test-123",
        "method": "tools/list",
        "params": {}
      }
    """.trimIndent(),
  )


  @Test
  fun `test MiskServerTransport start() will set initialized to true`() = runTest {
    // Verify initial state - transport should not be initialized
    // We can test this indirectly by trying to send a message, which should fail
    assertFailsWith<IllegalStateException> {
      transport.send(testMessage)
    }

    // Call start() and verify it succeeds
    transport.start()

    // Verify that after start(), the transport is initialized by successfully sending a message
    transport.send(testMessage)

    // Verify that the session.send was called
    coVerify { mockSession.send(event = "message", data = any()) }
  }

  @Test
  fun `test MiskServerTransport start() throws error if already started`() = runTest {
    // Call start() first time - should succeed
    transport.start()

    // Call start() second time - should throw error
    val exception = assertFailsWith<IllegalStateException> {
      transport.start()
    }

    assertTrue(exception.message!!.contains("already started"))
  }

  @Test
  fun `test MiskServerTransport send() fails if uninitialized`() = runTest {

    // Call send() without starting and verify that it throws an error
    val exception = assertFailsWith<IllegalStateException> {
      transport.send(testMessage)
    }

    assertEquals("Not connected", exception.message)

    // Verify that session.send was never called
    coVerify(exactly = 0) { mockSession.send(any<ServerSentEvent>()) }
    coVerify(exactly = 0) { mockSession.send(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `test MiskServerTransport send() succeeds when initialized`() = runTest {

    // Start the transport
    transport.start()

    // Call send() and verify it succeeds
    transport.send(testMessage)

    // Verify that session.send was called with correct parameters
    coVerify { mockSession.send(event = "message", data = any()) }
  }

  @Test
  fun `test MiskServerTransport close() will set initialized to false`() = runTest {
    // Start the transport first
    transport.start()

    // Verify transport is initialized by sending a message successfully
    transport.send(testMessage)
    coVerify { mockSession.send(event = "message", data = any()) }

    // Call close() and verify it succeeds
    transport.close()

    // Verify that session.close() was called
    verify { mockSession.close() }

    // Verify that after close(), the transport is no longer initialized
    // by trying to send a message, which should fail
    assertFailsWith<IllegalStateException> {
      transport.send(testMessage)
    }
  }

  @Test
  fun `test MiskServerTransport close() when not started does nothing`() = runTest {
    // Call close() without starting - should not throw error but should not call session.close()
    transport.close()

    // Verify that session.close() was not called since transport was never started
    verify(exactly = 0) { mockSession.close() }
  }

  @Test
  fun `test MiskServerTransport sessionId returns session sessionId`() {
    // Verify that sessionId property returns the session's sessionId
    assertEquals("test-session-123", transport.sessionId)

    // Verify that the session.sessionId was accessed
    verify { mockSession.sessionId }
  }

  @Test
  fun `test MiskServerTransport handleMessage calls onMessage callback`() = runTest {
    // Set up a callback to track if onMessage was called
    var onMessageCalled = false
    var receivedMessage: JSONRPCMessage? = null

    transport.onMessage { message ->
      onMessageCalled = true
      receivedMessage = message
    }

    // Call handleMessage
    transport.handleMessage(testMessage, "test-session")

    // Verify that the callback was called with the correct message
    assertTrue(onMessageCalled)
    assertEquals(testMessage, receivedMessage)
  }

  @Test
  fun `test MiskServerTransport handleMessage calls onError callback when exception occurs`() = runTest {
    // Set up callbacks to track if they were called
    var onErrorCalled = false
    var receivedException: Throwable? = null
    val testException = RuntimeException("Test exception")

    transport.onMessage {
      throw testException
    }

    transport.onError { exception ->
      onErrorCalled = true
      receivedException = exception
    }

    // Call handleMessage and expect it to throw the exception
    val thrownException = assertFailsWith<RuntimeException> {
      transport.handleMessage(testMessage, "test-session")
    }

    // Verify that the error callback was called with the correct exception
    assertTrue(onErrorCalled)
    assertEquals(testException, receivedException)
    assertEquals(testException, thrownException)
  }

}
