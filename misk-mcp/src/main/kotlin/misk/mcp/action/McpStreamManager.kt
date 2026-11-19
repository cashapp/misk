package misk.mcp.action

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.SendChannel
import misk.MiskCaller
import misk.annotation.ExperimentalMiskApi
import misk.mcp.MiskMcpServer
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

/**
 * Bridges [McpPost] web actions and the [MiskMcpServer], over Streamable HTTP.
 *
 * Each POST gets its own [McpStream], which closes when the request ends:
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
  private val mcpServer: MiskMcpServer,
  private val httpCall: ActionScoped<HttpCall>,
  private val caller: ActionScoped<MiskCaller?>,
) {
  /**
   * Runs [block] against an [McpStream] whose responses go to [sendChannel] as Server-Sent Events (SSE).
   *
   * @param sendChannel Server-Sent Events (SSE) channel for sending responses to the client
   * @param block Function executed with the stream as receiver
   */
  suspend fun withSseChannel(sendChannel: SendChannel<ServerSentEvent>, block: suspend McpStream.() -> Unit) {
    try {
      McpStream(
          server = mcpServer,
          httpCall = httpCall.get(),
          caller = caller.get(),
          sendChannel = sendChannel,
          keepAliveInterval = mcpServer.config.keep_alive_interval_ms.milliseconds,
        )
        .block()
    } finally {
      sendChannel.close()
    }
  }
}
