package misk.web

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class WebSocketsTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun basicWebSocket() {
    val client = OkHttpClient.Builder()
        .build()

    val httpServerUrl = jettyService.httpServerUrl
    val request = okhttp3.Request.Builder()
        .url("ws://${httpServerUrl.host()}:${httpServerUrl.port()}/echo")
        .build()

    val messages = LinkedBlockingDeque<String>()
    val webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
      override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        messages.add(text)
      }
    })

    val message = "this message will be echo'd back by the server"
    webSocket.send(message)
    assertEquals(message, messages.pollFirst(2, TimeUnit.SECONDS))
  }

  @Singleton
  class EchoWebSocket : WebAction {
    @ConnectWebSocket("/echo")
    fun echo(@Suppress("UNUSED_PARAMETER") webSocket: WebSocket): WebSocketListener {
      return object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
          webSocket.send(text)
        }
      }
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry(EchoWebSocket::class))
    }
  }
}
