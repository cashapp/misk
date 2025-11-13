@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.action.McpDelete
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.SESSION_ID_HEADER
import misk.mcp.action.handleMessage
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.testing.InMemoryMcpSessionHandler
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.mcp.testing.tools.SessionIdentifierOutput
import misk.mcp.testing.tools.SessionIdentifierTool
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.RequestBody
import misk.web.RequestHeader
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.sse.ServerSentEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.assertThrows
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@MiskTest(startService = true)
internal class McpStatefulServerActionTest {

  val mcpStatefulServerActionTestConfig = McpConfig(
    buildMap {
      put(
        SERVER_NAME,
        McpServerConfig(
          version = "1.0.0",
        ),
      )
    },
  )

  @OptIn(ExperimentalMiskApi::class)
  @Suppress("unused")
  @Singleton
  class McpStatefulServerActionTestPostAction @Inject constructor(private val mcpStreamManager: McpStreamManager) :
    WebAction {
    @McpPost
    suspend fun mcpPost(@RequestBody message: JSONRPCMessage, sendChannel: SendChannel<ServerSentEvent>) {
      mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
    }
  }

  @OptIn(ExperimentalMiskApi::class)
  @Suppress("unused")
  @Singleton
  class McpStatefulServerActionTestDeleteAction @Inject constructor(
    private val mcpSessionHandler: McpSessionHandler
  ) : WebAction {
    @McpDelete
    suspend fun deleteSession(@RequestHeader(SESSION_ID_HEADER) sessionId: String): Response<Unit> {
      mcpSessionHandler.terminate(sessionId)
      return Response(
        body = Unit,
        statusCode = 204, // No Content
      )
    }
  }

  @Suppress("unused")
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(McpServerModule.create(SERVER_NAME, mcpStatefulServerActionTestConfig))
      install(McpSessionHandlerModule.create<InMemoryMcpSessionHandler>())
      install(WebActionModule.create<McpStatefulServerActionTestPostAction>())
      install(WebActionModule.create<McpStatefulServerActionTestDeleteAction>())
      install(McpToolModule.create<SessionIdentifierTool>())

      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
    }
  }

  @Inject
  private lateinit var jettyService: JettyService

  private val okHttpClient = OkHttpClient()

  @Test
  fun `test ListTools`() = runBlocking {
    val mcpClient = okHttpClient.asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp")
    val request = ListToolsRequest()
    val response = mcpClient.listTools(request)
    assertEquals(
      expected = 1,
      actual = response.tools.size,
      message = "Expecting only one tool to be registered",
    )

    // Check session identifier tool
    val sessionIdentifierTool = response.tools.find { it.name == "session_identifier" }
    assertNotNull(sessionIdentifierTool)
    assertEquals(
      expected = "session_identifier",
      actual = sessionIdentifierTool.name,
      message = "Expected the session identifier tool name to be 'session_identifier'",
    )
    assertEquals(
      expected = "Returns the current MCP session ID",
      actual = sessionIdentifierTool.description,
      message = "Expected the correct description for session identifier tool",
    )

    // Check session identifier tool input schema
    assertNotNull(sessionIdentifierTool.inputSchema)
    assertEquals(
      expected = "object",
      actual = sessionIdentifierTool.inputSchema.type,
      message = "Expected input schema type to be 'object'",
    )
  }

  @Test
  fun `test SessionIdentifierTool returns session ID`() = runBlocking {
    val mcpClient = okHttpClient.asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp")

    // Call the session identifier tool
    val response = mcpClient.callTool(
      name = "session_identifier",
      arguments = mapOf("dummy" to "unused"),
    )

    assertNotNull(response)
    assertNotNull(response.structuredContent)

    // Parse the structured result
    val structuredResult = assertNotNull(response.structuredContent).decode<SessionIdentifierOutput>()

    val sessionIdFromTool = structuredResult.sessionId
    assertTrue(sessionIdFromTool.isNotBlank())

    // Get the session ID from the client's transport
    val clientTransport = mcpClient.transport as? StreamableHttpClientTransport
    assertNotNull(clientTransport)
    val clientSessionId = clientTransport.sessionId

    // Verify they match
    assertEquals(
      expected = clientSessionId,
      actual = sessionIdFromTool,
      message = "Session ID from tool should match the client's transport session ID",
    )
  }

  @Test
  fun `test session deletion invalidates session ID tool`() = runBlocking {
    // Initialize a session by creating an MCP client
    val mcpClient = okHttpClient.asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp")

    // Get the session ID from the client's transport
    val clientTransport = assertNotNull(mcpClient.transport as? StreamableHttpClientTransport)
    val sessionId = assertNotNull(clientTransport.sessionId)

    // Verify the session works initially by calling the session identifier tool
    val initialResponse = mcpClient.callTool(
      name = "session_identifier",
      arguments = mapOf("dummy" to "unused"),
    )
    assertNotNull(initialResponse)
    assertNotNull(initialResponse.structuredContent)

    // Delete the session using HTTP DELETE
    val deleteUrl = jettyService.httpServerUrl.newBuilder()
      .encodedPath("/mcp")
      .build()
    val deleteRequest = Request.Builder()
      .url(deleteUrl)
      .delete()
      .addHeader(SESSION_ID_HEADER, sessionId)
      .build()

    val deleteResponse = okHttpClient.newCall(deleteRequest).execute()
    assertEquals(HTTP_NO_CONTENT, deleteResponse.code)

    // Now try to call the session identifier tool again - should fail with 404
    val error = assertThrows<StreamableHttpError> {
      mcpClient.callTool(
        name = "session_identifier",
        arguments = mapOf("dummy" to "unused"),
      )
    }
    assertEquals(HTTP_NOT_FOUND, error.code)
  }

  @Test
  fun `test missing session ID after session establishment`() = runBlocking {
    // Initialize a session by creating an MCP client
    val mcpClient = okHttpClient.asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp")


    // Verify the session works initially by calling the session identifier tool
    val initialResponse = mcpClient.callTool(
      name = "session_identifier",
      arguments = mapOf("dummy" to "unused"),
    )
    assertNotNull(initialResponse)
    assertNotNull(initialResponse.structuredContent)

    // Now remove the session ID from the client's transport to simulate a missing session ID
    // Use the reflection hack to work around the set call being private
    val clientTransport = assertNotNull(mcpClient.transport as? StreamableHttpClientTransport)
    StreamableHttpClientTransport::class.java.getDeclaredField("sessionId").apply {
      isAccessible = true
      set(clientTransport, null) // Simulate missing session ID
    }

    // Now try to call the session identifier tool again - should fail with 404
    val error = assertThrows<StreamableHttpError> {
      mcpClient.callTool(
        name = "session_identifier",
        arguments = mapOf("dummy" to "unused"),
      )
    }
    assertEquals(HTTP_BAD_REQUEST, error.code)
  }

  companion object {
    private const val SERVER_NAME = "mcp-stateful-server-action-test-server"
  }
}





