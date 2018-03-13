package misk.web.jetty

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okio.Utf8
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.api.WriteCallback
import java.util.ArrayDeque

private const val MAX_QUEUE_SIZE = 16 * 1024 * 1024

class RealWebSocket : WebSocket<String> {

  /** Total size of messages enqueued and not yet transmitted by Jetty. */
  private var outgoingQueueSize = 0L

  /** Messages to send when the Web Socket connects. */
  private var queue = ArrayDeque<String>()

  /** Application's listener to notify of incoming messages from the client. */
  lateinit var listener: WebSocketListener<Any>
  lateinit var socket: WebSocket<Any>

  val adapter = object : WebSocketAdapter() {
    override fun onWebSocketConnect(session: Session?) {
      super.onWebSocketConnect(session)
      sendQueue()
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
      super.onWebSocketClose(statusCode, reason)
      listener.onClosed(socket, statusCode, reason!!)
    }

    override fun onWebSocketError(cause: Throwable?) {
      listener.onFailure(socket, cause!!)
    }

    override fun onWebSocketText(message: String?) {
      super.onWebSocketText(message)
      listener.onMessage(socket, message!!)
    }
  }

  override fun queueSize(): Long {
    return outgoingQueueSize
  }

  override fun send(content: String): Boolean {
    val byteCount = Utf8.size(content)
    if (outgoingQueueSize + byteCount > MAX_QUEUE_SIZE) {
      close(1001, null)
      return false
    }

    outgoingQueueSize += byteCount
    queue.add(content)
    sendQueue()

    return true
  }

  private fun sendQueue() {
    while (adapter.isConnected && queue.isNotEmpty()) {
      val text = queue.pop()
      val byteCount = Utf8.size(text)

      adapter.remote.sendString(text, object : WriteCallback {
        override fun writeSuccess() {
          outgoingQueueSize -= byteCount
        }

        override fun writeFailed(x: Throwable?) {
          outgoingQueueSize -= byteCount
        }
      })
    }
  }

  override fun close(code: Int, reason: String?): Boolean {
    adapter.session.close(code, reason)
    return false
  }

  override fun cancel() {
    adapter.session.disconnect()
  }
}
