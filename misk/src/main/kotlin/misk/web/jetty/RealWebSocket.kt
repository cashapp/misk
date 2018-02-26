package misk.web.jetty

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okio.ByteString
import okio.Utf8
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.api.WriteCallback
import java.util.ArrayDeque

private const val MAX_QUEUE_SIZE = 16 * 1024 * 1024

class RealWebSocket : WebSocket {

  /** Total size of messages enqueued and not yet transmitted by Jetty. */
  private var outgoingQueueSize = 0L

  /** Messages to send when the Web Socket connects. */
  private var queue = ArrayDeque<String>()

  /** Application's listener to notify of incoming messages from the client. */
  lateinit var listener: WebSocketListener

  val adapter = object : WebSocketAdapter() {
    override fun onWebSocketConnect(sess: Session?) {
      super.onWebSocketConnect(sess)
      sendQueue()
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
      super.onWebSocketClose(statusCode, reason)
      listener.onClosed(this@RealWebSocket, statusCode, reason!!)
    }

    override fun onWebSocketError(cause: Throwable?) {
      listener.onFailure(this@RealWebSocket, cause!!)
    }

    override fun onWebSocketText(message: String?) {
      super.onWebSocketText(message)
      listener.onMessage(this@RealWebSocket, message!!)
    }

    override fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int) {
      super.onWebSocketBinary(payload, offset, len)
      listener.onMessage(this@RealWebSocket, ByteString.of(payload!!, offset, len))
    }
  }

  override fun queueSize(): Long {
    return outgoingQueueSize
  }

  override fun send(text: String): Boolean {
    val byteCount = Utf8.size(text)
    if (outgoingQueueSize + byteCount > MAX_QUEUE_SIZE) {
      close(1001, null)
      return false
    }

    outgoingQueueSize += byteCount
    queue.add(text)
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

  override fun send(bytes: ByteString): Boolean {
    TODO()
  }

  override fun close(code: Int, reason: String?): Boolean {
    adapter.session.close(code, reason)
    return false
  }

  override fun cancel() {
    adapter.session.disconnect()
  }
}
