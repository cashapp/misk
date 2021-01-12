package misk.web

import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.interceptors.LogRequestResponse
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class WebSocketsTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var logCollector: LogCollector

  val listener = FakeWebSocketListener()

  @Test
  fun basicWebSocket() {
    val client = OkHttpClient()

    val request = Request.Builder()
        .url(jettyService.httpServerUrl.resolve("/echo")!!)
        .build()

    val webSocket = client.newWebSocket(request, listener)

    webSocket.send("hello")
    assertEquals("ACK hello", listener.takeMessage())

    // Confirm interceptors were invoked.
    assertThat(logCollector.takeMessage(RequestLoggingInterceptor::class)).matches(
      "EchoWebSocket principal=unknown time=0.000 ns code=200 request=\\[JettyWebSocket\\[.* to /echo]] response=EchoListener"
    )
  }

  @Singleton
  class EchoWebSocket @Inject constructor() : WebAction {
    @ConnectWebSocket("/echo")
    @LogRequestResponse(bodySampling = 1.0, errorBodySampling = 1.0)
    fun echo(@Suppress("UNUSED_PARAMETER") webSocket: WebSocket): WebSocketListener {
      return object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
          webSocket.send("ACK $text")
        }

        override fun toString() = "EchoListener"
      }
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(LogCollectorModule())
      install(WebActionModule.create<EchoWebSocket>())
    }
  }
}
