package misk.web

import com.google.gson.Gson
import com.google.inject.util.Modules
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
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
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule())

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var gson: Gson

  @Test fun basicWebSocket() {
    val message = "this message will be echo'd back by the server"
    val reply = sendAndReceiveWebSocketMessage("/echo", message)
    assertEquals(message, reply)
  }

  @Test fun jsonWebSocket() {
    val message = JsonWebSocket.Message("client", 5)
    val reply = sendAndReceiveWebSocketMessage("/json", gson.toJson(message))
    assertEquals("{\"a\":\"server\",\"b\":5}", reply)
  }

  @Singleton
  class EchoWebSocket : WebAction {
    @ConnectWebSocket("/echo")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun echo(webSocket: WebSocket<String>): WebSocketListener<String> {
      return object : WebSocketListener<String>() {
        override fun onMessage(webSocket: WebSocket<String>, content: String) {
          webSocket.send(content)
        }
      }
    }
  }

  @Singleton
  class JsonWebSocket : WebAction {
    @ConnectWebSocket("/json")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun json(webSocket: WebSocket<Message>): WebSocketListener<Message> {
      return object : WebSocketListener<Message>() {
        override fun onMessage(webSocket: WebSocket<Message>, content: Message) {
          webSocket.send(Message("server", content.b))
        }
      }
    }

    data class Message(
      val a: String,
      val b: Int
    )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<EchoWebSocket>())
      install(WebActionModule.create<JsonWebSocket>())
    }
  }

  private fun sendAndReceiveWebSocketMessage(path: String, message: String): String {
    val client = OkHttpClient.Builder()
        .build()

    val httpServerUrl = jettyService.httpServerUrl
    val request = okhttp3.Request.Builder()
        .url("ws://${httpServerUrl.host()}:${httpServerUrl.port()}$path")
        .build()

    val messages = LinkedBlockingDeque<String>()
    val webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
      override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        messages.add(text)
      }
    })

    webSocket.send(message)
    return messages.pollFirst(2, TimeUnit.SECONDS)
  }
}
