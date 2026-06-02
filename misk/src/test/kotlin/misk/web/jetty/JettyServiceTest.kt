package misk.web.jetty

import java.nio.file.InvalidPathException
import misk.security.ssl.SslLoader
import misk.web.WebConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.util.MultiException
import org.eclipse.jetty.util.thread.ThreadPool
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class JettyServiceTest {

  @Test
  fun isJEP380Supported() {
    assertThat(isJEP380Supported("", 16)).isFalse()
    assertThat(isJEP380Supported("@socket.sock", 16)).isFalse()
    assertThat(isJEP380Supported("\u0000socket.sock", 16)).isFalse()
    assertThat(isJEP380Supported("socket.sock", 15)).isFalse()
    assertThat(isJEP380Supported("socket.sock", 16)).isTrue()
    assertThat(isJEP380Supported("/socket.sock", 16)).isTrue()
  }

  @Test
  fun `stop suppresses MultiException when all nested exceptions are InvalidPathException`() {
    val server = mock(Server::class.java)
    val multi = MultiException()
    multi.add(InvalidPathException("http-ingress.sock", "Nul character not allowed"))
    multi.add(InvalidPathException("istio-proxy.sock", "Nul character not allowed"))
    multi.add(InvalidPathException("grpc-ingress.sock", "Nul character not allowed"))
    `when`(server.isRunning).thenReturn(true)
    doThrow(multi).`when`(server).stop()

    val jettyService = jettyService(server)

    assertThatCode { jettyService.stop() }.doesNotThrowAnyException()
  }

  @Test
  fun `stop rethrows MultiException when shutdown failures include non InvalidPathException`() {
    val server = mock(Server::class.java)
    val multi = MultiException()
    multi.add(InvalidPathException("http-ingress.sock", "Nul character not allowed"))
    multi.add(RuntimeException("unexpected shutdown failure"))
    `when`(server.isRunning).thenReturn(true)
    doThrow(multi).`when`(server).stop()

    val jettyService = jettyService(server)

    assertThatThrownBy { jettyService.stop() }.isSameAs(multi)
  }

  private fun jettyService(server: Server): JettyService {
    val jettyService =
      JettyService(
        sslLoader = mock(SslLoader::class.java),
        webActionsServlet = mock(WebActionsServlet::class.java),
        webConfig = WebConfig(port = 0),
        threadPool = mock(ThreadPool::class.java),
        connectionMetricsCollector = mock(JettyConnectionMetricsCollector::class.java),
        statisticsHandler = mock(StatisticsHandler::class.java),
        gzipHandler = mock(GzipHandler::class.java),
        http2RateControlFactory = mock(MeasuredWindowRateControl.Factory::class.java),
      )

    JettyService::class.java.getDeclaredField("server").apply {
      isAccessible = true
      set(jettyService, server)
    }

    return jettyService
  }
}
