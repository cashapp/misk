package misk.web.actions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withTimeout
import misk.Action
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.sse.ServerSentEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class SseCancellationTest {
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        // Keep the connector's idle timeout much longer than the action deadline.
        install(WebServerTestingModule())
        install(MiskTestingServiceModule())
        install(WebActionModule.create<DeadlineAction>())
        multibind<NetworkInterceptor.Factory>().to<CompletionProbe>()
      }
    }

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var probe: CompletionProbe

  @Test
  fun `action deadline cancels a blocked SSE write without waiting for client disconnect`() {
    Socket().use { socket ->
      socket.receiveBufferSize = 1024
      socket.connect(InetSocketAddress(jetty.httpServerUrl.host, jetty.httpServerUrl.port))
      socket
        .getOutputStream()
        .write("GET /deadline-sse HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\n\r\n".toByteArray())
      // Deliberately never read the response. Eventually the servlet flush blocks on the full socket.
      probe.started.get(5, SECONDS)
      probe.completed.get(5, SECONDS)
      assertThat(probe.deadlineReached.isDone).isTrue()
      assertThat(socket.isClosed).isFalse()
    }
  }

  @Singleton
  class CompletionProbe @Inject constructor() : NetworkInterceptor.Factory {
    val started = CompletableFuture<Unit>()
    val completed = CompletableFuture<Unit>()
    val deadlineReached = CompletableFuture<Unit>()

    override fun create(action: Action): NetworkInterceptor? {
      if (action.function.name != "stream") return null
      return object : NetworkInterceptor {
        override fun intercept(chain: NetworkChain) {
          started.complete(Unit)
          try {
            chain.proceed(chain.httpCall)
          } finally {
            // Action completion alone is insufficient: the response bridge must have terminated too.
            completed.complete(Unit)
          }
        }
      }
    }
  }

  class DeadlineAction @Inject constructor(private val probe: CompletionProbe) : WebAction {
    @Get("/deadline-sse")
    @ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
    @Unauthenticated
    suspend fun stream(output: SendChannel<ServerSentEvent>) {
      val event = ServerSentEvent(data = "x".repeat(64 * 1024))
      try {
        withTimeout(1_000) { while (true) output.send(event) }
      } catch (e: TimeoutCancellationException) {
        probe.deadlineReached.complete(Unit)
        throw e
      }
    }
  }
}
