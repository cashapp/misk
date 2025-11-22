package misk.web

import com.squareup.protos.test.parsing.Warehouse
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
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
import misk.logging.LogCollector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import okio.ByteString

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
      "EchoWebSocket principal=unknown time=0.000 ns code=200 " +
        "request=JettyWebSocket\\[.* to /echo] response=EchoListener"
    )
  }

  @Test
  fun binaryWebSocket() {
    val client = OkHttpClient()

    val request = Request.Builder()
      .url(jettyService.httpServerUrl.resolve("/echo")!!)
      .build()

    val webSocket = client.newWebSocket(request, listener)
    val warehouse = Warehouse.Builder()
      .warehouse_token("WH_1")
      .warehouse_id(42)
      .build()

    webSocket.send(warehouse.encodeByteString())

    val expected = Warehouse.Builder()
      .warehouse_token("ACK WH_1")
      .warehouse_id(43)
      .build()
    val actual = listener.takeBinaryMessage()?.let { Warehouse.ADAPTER.decode(it) }
    assertEquals(actual, expected)
  }

  @Test
  fun loggingDisabledByEnv() {
    val client = OkHttpClient()

    val request = Request.Builder()
      .url(jettyService.httpServerUrl.resolve("/echo-logging-disabled-by-env")!!)
      .build()

    val webSocket = client.newWebSocket(request, listener)

    webSocket.send("hello")
    assertEquals("ACK hello", listener.takeMessage())

    // Confirm request logging interceptor was not invoked.
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).isEmpty()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(LogCollectorModule())
      install(WebActionModule.create<EchoWebSocket>())
    }
  }
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

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val message = Warehouse.ADAPTER.decode(bytes)
        webSocket.send(
          message.newBuilder()
            .warehouse_token("ACK ${message.warehouse_token}")
            .warehouse_id(message.warehouse_id + 1)
            .build()
            .encodeByteString()
        )
      }

      override fun toString() = "EchoListener"
    }
  }

  @ConnectWebSocket("/echo-logging-disabled-by-env")
  @LogRequestResponse(
    bodySampling = 1.0,
    errorBodySampling = 1.0,
    excludedEnvironments = ["testing"]
  )
  fun echoLoggingDisabledByEnv(@Suppress("UNUSED_PARAMETER") webSocket: WebSocket): WebSocketListener {
    return object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        webSocket.send("ACK $text")
      }

      override fun toString() = "EchoListener"
    }
  }
}
