package com.squareup.chat

import com.google.inject.util.Modules
import com.squareup.chat.actions.ChatWebSocketAction
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.redis.RedisModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeWebSocket
import misk.web.WebActionModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import jakarta.inject.Inject
import misk.redis.testing.RedisTestFlushModule
import redis.clients.jedis.ConnectionPoolConfig

@MiskTest(startService = true)
class ChatWebSocketActionTest {
  @Suppress("unused")
  @MiskTestModule
  private val module = Modules.combine(
    MiskTestingServiceModule(),
    DeploymentModule(TESTING),
    RedisModule(DockerRedis.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false),
    RedisTestFlushModule(),
    WebActionModule.create<ChatWebSocketAction>()
  )

  @Inject lateinit var chatWebSocketAction: ChatWebSocketAction

  @Test fun happyPath() {
    val sandyWebSocket = FakeWebSocket()
    val randyWebSocket = FakeWebSocket()
    val sandyListener = chatWebSocketAction.chat("discuss", sandyWebSocket)
    val randyListener = chatWebSocketAction.chat("discuss", randyWebSocket)

    assertThat(sandyWebSocket.poll()).isEqualTo("send: Welcome to discuss!")
    assertThat(sandyWebSocket.poll()).isEqualTo("send: Subscribed to discuss")
    assertThat(randyWebSocket.poll()).isEqualTo("send: Welcome to discuss!")
    assertThat(randyWebSocket.poll()).isEqualTo("send: Subscribed to discuss")

    sandyListener.onMessage(sandyWebSocket, "hello from sandy")
    randyListener.onMessage(sandyWebSocket, "hello from randy")

    assertThat(sandyWebSocket.poll()).isEqualTo("send: hello from sandy")
    assertThat(sandyWebSocket.poll()).isEqualTo("send: hello from randy")
    assertThat(randyWebSocket.poll()).isEqualTo("send: hello from sandy")
    assertThat(randyWebSocket.poll()).isEqualTo("send: hello from randy")
  }
}
