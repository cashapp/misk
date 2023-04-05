package misk.web

import misk.web.actions.WebSocket
import okio.ByteString
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class FakeWebSocket : WebSocket {
  private val log = LinkedBlockingDeque<String>()

  override fun queueSize(): Long {
    return 0L
  }

  override fun send(bytes: ByteString): Boolean {
    return log.offer("send: ${bytes.hex()}")
  }

  override fun send(text: String): Boolean {
    return log.offer("send: $text")
  }

  override fun close(code: Int, reason: String?): Boolean {
    return log.offer("close")
  }

  override fun cancel() {
    log.offer("cancel")
  }

  fun poll(): String? {
    return log.poll(5, TimeUnit.SECONDS)
  }
}
