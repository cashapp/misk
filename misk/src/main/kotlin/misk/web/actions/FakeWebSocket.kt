package misk.web.actions

import okio.ByteString

class FakeWebSocket : WebSocket {
  private val log = mutableListOf<String>()

  override fun queueSize(): Long {
    return 0L
  }

  override fun send(bytes: ByteString): Boolean {
    log.add("send: ${bytes.hex()}")
    return true
  }

  override fun send(text: String): Boolean {
    log.add("send: $text")
    return true
  }

  override fun close(code: Int, reason: String?): Boolean {
    log.add("close")
    return true
  }

  override fun cancel() {
    log.add("cancel")
  }

  fun takeLog(): List<String> {
    val result = log.toList()
    log.clear()
    return result
  }
}
