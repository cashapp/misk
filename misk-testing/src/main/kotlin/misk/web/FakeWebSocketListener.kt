package misk.web

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import okio.ByteString

class FakeWebSocketListener : okhttp3.WebSocketListener() {
  val messages = LinkedBlockingDeque<String>()
  val binaryMessages = LinkedBlockingDeque<ByteString>()

  override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
    messages.add(text)
  }

  override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
    binaryMessages.add(bytes)
  }

  fun takeMessage() = messages.pollFirst(2, TimeUnit.SECONDS)

  fun takeBinaryMessage() = binaryMessages.pollFirst(2, TimeUnit.SECONDS)
}
