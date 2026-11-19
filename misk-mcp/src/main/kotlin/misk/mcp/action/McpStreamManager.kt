package misk.mcp.action

import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.logging.getLogger
import misk.mcp.MiskMcpServer
import misk.mcp.internal.MiskServerTransport
import misk.mcp.internal.MiskStreamableHttpServerTransport
import misk.web.sse.ServerSentEvent

/**
 * Bridges [McpPost] web actions and the [MiskMcpServer], over Streamable HTTP.
 *
 * Each POST gets its own transport and server session, which close when the request ends:
 * ```kotlin
 * @Singleton
 * class McpPostAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpPost
 *   suspend fun handleMcpRequest(
 *     @RequestBody message: JSONRPCMessage,
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
 *   }
 * }
 * ```
 *
 * @see MiskMcpServer For the MCP server implementation
 * @see McpPost For the web action annotation
 */
@ExperimentalMiskApi
class McpStreamManager
internal constructor(
  private val streamableHttpServerTransportFactory: MiskStreamableHttpServerTransport.Factory,
  private val mcpServer: MiskMcpServer,
) {
  /**
   * Runs [block] against a server session whose responses go to [sendChannel] as Server-Sent Events (SSE).
   *
   * @param sendChannel Server-Sent Events (SSE) channel for sending responses to the client
   * @param block Function executed with the server session as receiver
   */
  suspend fun withSseChannel(sendChannel: SendChannel<ServerSentEvent>, block: suspend ServerSession.() -> Unit) {
    val transport =
      streamableHttpServerTransportFactory.create(sendChannel).also {
        logger.debug { "New StreamableHTTP connection established with streamId: ${it.streamId}" }
      }

    val session = mcpServer.createSession(transport).initialize()

    try {
      block(session)
    } finally {
      transport.close()
    }
  }

  private fun ServerSession.initialize(): ServerSession {
    val miskServerTransport =
      checkNotNull(transport as? MiskServerTransport) {
        "Expected transport to be MiskServerTransport but was ${transport?.let { it::class.simpleName }}"
      }
    val serverName = mcpServer.name
    val streamId = miskServerTransport.streamId

    onInitialized { logger.info { "Initialized mcp server session for '$serverName' with stream id '$streamId'" } }
    onClose { logger.info { "Closed mcp server session for '$serverName' with stream id '$streamId'" } }
    logger.debug { "Server Session connected to transport with stream id '$streamId'" }
    return this
  }

  companion object {
    private val logger = getLogger<McpStreamManager>()
  }
}
