package com.squareup.chat

import com.google.inject.util.Modules
import com.squareup.chat.actions.ChatWebSocketAction
import misk.MiskModule
import misk.eventrouter.EventRouterTester
import misk.eventrouter.EventRouterTestingModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.FakeWebSocket
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class ChatWebSocketActionTest {
  @MiskTestModule
  val module = Modules.combine(MiskModule(), EventRouterTestingModule())

  @Inject lateinit var chatWebSocketAction: ChatWebSocketAction
  @Inject lateinit var eventRouterTester: EventRouterTester

  @Test fun test() {
    val sandyWebSocket = FakeWebSocket()
    val randyWebSocket = FakeWebSocket()
    val sandyListener = chatWebSocketAction.chat("discuss", sandyWebSocket)
    chatWebSocketAction.chat("discuss", randyWebSocket)

    assertThat(randyWebSocket.takeLog()).containsExactly("send: Welcome to discuss!")
    assertThat(sandyWebSocket.takeLog()).containsExactly("send: Welcome to discuss!")
    sandyListener.onMessage(sandyWebSocket, "hello from sandy")

    eventRouterTester.processEverything()
    assertThat(randyWebSocket.takeLog()).containsExactly("send: hello from sandy")
  }
}
