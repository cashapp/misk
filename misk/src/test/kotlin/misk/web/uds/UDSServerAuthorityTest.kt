package misk.web.uds

import jakarta.inject.Inject
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebUnixDomainSocketConfig
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Jetty 12 derives a request's server authority from the connection's local address when the request carries no
 * Host/:authority. On a Unix domain socket that local address is the socket file path, which
 * [org.eclipse.jetty.util.HostPort] rejects as an invalid authority -- it logs "Bad Authority" and throws, failing the
 * request with a 400 before it ever reaches a web action.
 *
 * [JettyService] avoids that by setting a fallback server authority on the Unix-domain connector's HttpConfiguration.
 */
@MiskTest(startService = true)
class UDSServerAuthorityTest {
  @MiskTestModule val module = TestModule()

  /** An HTTP/1.0 request is valid without a Host header, so Jetty must fall back to the authority. */
  @Test
  fun `request without a Host header is served`() {
    val response = sendRaw("GET /hello HTTP/1.0\r\n\r\n")

    assertThat(response).startsWith("HTTP/1.1 200")
    assertThat(response).endsWith("hello")
  }

  private fun sendRaw(request: String): String {
    SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
      channel.connect(UnixDomainSocketAddress.of(socketPath))
      channel.write(ByteBuffer.wrap(request.toByteArray(Charsets.US_ASCII)))

      val response = StringBuilder()
      val buffer = ByteBuffer.allocate(1024)
      while (channel.read(buffer) != -1) {
        buffer.flip()
        response.append(Charsets.US_ASCII.decode(buffer))
        buffer.clear()
      }
      return response.toString()
    }
  }

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello") @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8) fun sayHello() = "hello"
  }

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig =
            WebServerTestingModule.TESTING_WEB_CONFIG.copy(
              unix_domain_sockets = listOf(WebUnixDomainSocketConfig(path = socketPath))
            )
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
    }
  }

  companion object {
    // Keep this short: Unix domain socket paths are capped near 104 bytes on macOS.
    private val socketPath = Files.createTempDirectory("uds").resolve("authority.sock").toString()
  }
}
