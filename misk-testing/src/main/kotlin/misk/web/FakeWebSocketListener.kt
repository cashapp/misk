package misk.web

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class FakeWebSocketListener : okhttp3.WebSocketListener() {
  val messages = LinkedBlockingDeque<String>()

  override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
    messages.add(text)
  }

  fun takeMessage() = messages.pollFirst(2, TimeUnit.SECONDS)
}
