package misk.web.jetty

import misk.web.BoundAction
import misk.web.DispatchMechanism
import misk.web.ServletHttpCall
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okhttp3.Headers
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.utf8Size
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.api.WriteCallback
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import java.util.ArrayDeque

private const val MAX_QUEUE_SIZE = 16 * 1024 * 1024

internal class JettyWebSocket(
  val request: ServletUpgradeRequest,
  val response: ServletUpgradeResponse
) : WebSocket {

  /** Total size of messages enqueued and not yet transmitted by Jetty. */
  private var outgoingQueueSize = 0L

  /** Messages to send when the Web Socket connects. */
  private var queue = ArrayDeque<String>()

  /** Application's listener to notify of incoming messages from the client. */
  private var listener: WebSocketListener? = null

  private val adapter = object : WebSocketAdapter() {
    override fun onWebSocketConnect(sess: Session?) {
      super.onWebSocketConnect(sess)
      sendQueue()
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
      super.onWebSocketClose(statusCode, reason)
      listener!!.onClosed(this@JettyWebSocket, statusCode, reason)
    }

    override fun onWebSocketError(cause: Throwable?) {
      listener!!.onFailure(this@JettyWebSocket, cause!!)
    }

    override fun onWebSocketText(message: String?) {
      listener!!.onMessage(this@JettyWebSocket, message!!)
    }

    override fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int) {
      listener!!.onMessage(this@JettyWebSocket, payload!!.toByteString(offset, len))
    }
  }

  internal fun upstreamResponse() = object : ServletHttpCall.UpstreamResponse {
    override var statusCode: Int
      get() = response.statusCode
      set(value) {
        response.statusCode = value
      }

    override val headers: Headers
      get() = response.headers()

    override fun setHeader(name: String, value: String) {
      response.setHeader(name, value)
    }

    override fun addHeaders(headers: Headers) {
      for (i in 0 until headers.size) {
        response.addHeader(headers.name(i), headers.value(i))
      }
    }

    override fun requireTrailers() = error("no trailers for web sockets")

    override fun setTrailer(name: String, value: String) = error("no trailers for web sockets")

    override fun initWebSocketListener(webSocketListener: WebSocketListener) {
      check(this@JettyWebSocket.listener == null) { "web socket listener already set" }
      this@JettyWebSocket.listener = webSocketListener
    }
  }

  override fun queueSize(): Long {
    return outgoingQueueSize
  }

  override fun send(text: String): Boolean {
    val byteCount = text.utf8Size()
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
      val byteCount = text.utf8Size()

      adapter.remote.sendString(
        text,
        object : WriteCallback {
          override fun writeSuccess() {
            outgoingQueueSize -= byteCount
          }

          override fun writeFailed(x: Throwable?) {
            outgoingQueueSize -= byteCount
          }
        }
      )
    }
  }

  override fun send(bytes: ByteString): Boolean {
    TODO()
  }

  override fun close(code: Int, reason: String?): Boolean {
    if (adapter.isConnected) {
      adapter.session.close(code, reason)
      return true
    }
    return false
  }

  override fun cancel() {
    adapter.session.disconnect()
  }

  override fun toString(): String {
    val re = request.httpServletRequest
    return "JettyWebSocket[${re.remoteAddr}:${re.remotePort} to ${re.requestURI}]"
  }

  internal class Creator(
    private val boundActions: Set<BoundAction<out WebAction>>
  ) : WebSocketCreator {
    override fun createWebSocket(
      request: ServletUpgradeRequest,
      response: ServletUpgradeResponse
    ): WebSocketAdapter? {
      val realWebSocket = JettyWebSocket(request, response)

      val httpCall = ServletHttpCall.create(
        request = request.httpServletRequest,
        dispatchMechanism = DispatchMechanism.WEBSOCKET,
        upstreamResponse = realWebSocket.upstreamResponse(),
        webSocket = realWebSocket
      )

      val candidateActions = boundActions.mapNotNull {
        it.match(DispatchMechanism.WEBSOCKET, null, listOf(), httpCall.url)
      }

      val bestAction = candidateActions.sorted().firstOrNull() ?: return null
      bestAction.action.scopeAndHandle(request.httpServletRequest, httpCall, bestAction.pathMatcher)
      return realWebSocket.adapter
    }
  }
}
