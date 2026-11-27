package misk.web

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.scope.ActionScoped
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

@MiskTest(startService = true)
internal class WebSocketsTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var logCollector: LogCollector

  val listener = FakeWebSocketListener()

  @Test
  fun basicWebSocket() {
    val client = OkHttpClient()

    val request = Request.Builder().url(jettyService.httpServerUrl.resolve("/echo")!!).build()

    val webSocket = client.newWebSocket(request, listener)

    webSocket.send("hello")
    assertEquals("ACK hello", listener.takeMessage())

    // Confirm interceptors were invoked.
    assertThat(logCollector.takeMessage(RequestLoggingInterceptor::class))
      .matches(
        "EchoWebSocket principal=unknown time=0.000 ns code=200 " +
          "request=JettyWebSocket\\[.* to /echo] response=EchoListener"
      )
  }

  /**
   * A WebSocket upgrade must report the transport it arrived over, exactly as an ordinary request does. Authenticators
   * that trust a request because it arrived on a Unix domain socket or on plaintext loopback from a sidecar read this
   * value first; when it is null they cannot tell "no address" from "untrusted address" and reject the upgrade.
   */
  @Test
  fun webSocketUpgradeReportsLinkLayerLocalAddress() {
    val client = OkHttpClient()

    val request = Request.Builder().url(jettyService.httpServerUrl.resolve("/socket-address")!!).build()

    val webSocket = client.newWebSocket(request, listener)

    webSocket.send("what transport did I arrive on?")
    // The test server is a TCP connector, so a correctly populated call reports Network. Before the fix this was null.
    assertThat(listener.takeMessage()).startsWith("Network:")
  }

  @Test
  fun loggingDisabledByEnv() {
    val client = OkHttpClient()

    val request = Request.Builder().url(jettyService.httpServerUrl.resolve("/echo-logging-disabled-by-env")!!).build()

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
      install(WebActionModule.create<SocketAddressWebSocket>())
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

      override fun toString() = "EchoListener"
    }
  }

  @ConnectWebSocket("/echo-logging-disabled-by-env")
  @LogRequestResponse(bodySampling = 1.0, errorBodySampling = 1.0, excludedEnvironments = ["testing"])
  fun echoLoggingDisabledByEnv(@Suppress("UNUSED_PARAMETER") webSocket: WebSocket): WebSocketListener {
    return object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        webSocket.send("ACK $text")
      }

      override fun toString() = "EchoListener"
    }
  }
}

/** Echoes back the transport the upgrade arrived on, so a test can assert the call carries one. */
@Singleton
class SocketAddressWebSocket
@Inject
constructor(private val clientHttpCall: @JvmSuppressWildcards ActionScoped<HttpCall>) : WebAction {
  @ConnectWebSocket("/socket-address")
  fun connect(@Suppress("UNUSED_PARAMETER") webSocket: WebSocket): WebSocketListener {
    // Read during upgrade negotiation, which is when an authenticator would read it.
    val address = clientHttpCall.get().linkLayerLocalAddress
    val described =
      when (address) {
        null -> "null"
        is SocketAddress.Network -> "Network:${address.port}"
        is SocketAddress.Unix -> "Unix:${address.path}"
      }
    return object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        webSocket.send(described)
      }
    }
  }
}
