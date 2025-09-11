package misk.mcp.internal

import kotlinx.coroutines.channels.SendChannel
import misk.logging.getLogger
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent
import java.util.UUID


/**
 * Represents a single Server-Sent Events (SSE) stream for MCP communication.
 *
 * Manages the SSE connection lifecycle and provides methods to send events to the client.
 * Each session has a unique identifier for tracking and debugging purposes.
 *
 * @param call The HTTP call context for this session
 * @param sendChannel The channel used to send SSE events to the client
 */
internal class MiskSseServerStream(
  val call: HttpCall,
  private val sendChannel: SendChannel<ServerSentEvent>,
) {

  val streamId: String = UUID.randomUUID().toString()

  suspend fun send(event: ServerSentEvent) {
    logger.trace { "Sending SSE: $event" }
    sendChannel.send(event)
  }

  @JvmOverloads
  suspend fun send(
    data: String? = null,
    event: String? = null,
    id: String? = null,
    retry: Long? = null,
    comments: String? = null
  ) {
    send(ServerSentEvent(data, event, id, retry, comments))
  }

  fun close() {
    sendChannel.close()
  }

  companion object {
    private val logger = getLogger<MiskSseServerStream>()
  }
}
