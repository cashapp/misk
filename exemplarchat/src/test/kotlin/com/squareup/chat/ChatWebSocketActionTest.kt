package com.squareup.chat

import misk.testing.MiskTest
import misk.web.actions.WebSocket
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class ChatWebSocketActionTest {
  @Inject lateinit var chatWebSocketAction: ChatWebSocketAction

  @Test fun test() {
    val sandyWebSocket = FakeWebSocket()
    val sandyListener = chatWebSocketAction.chat("discuss", sandyWebSocket)

    val randyWebSocket = FakeWebSocket()
    chatWebSocketAction.chat("discuss", randyWebSocket)
    assertThat(randyWebSocket.takeLog()).containsExactly("send: Welcome to discuss!")

    sandyListener.onMessage(sandyWebSocket, "hello world")
    assertThat(randyWebSocket.takeLog()).containsExactly("send: hello world")
  }

  class FakeWebSocket : WebSocket {
    val log = mutableListOf<String>()

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
}
