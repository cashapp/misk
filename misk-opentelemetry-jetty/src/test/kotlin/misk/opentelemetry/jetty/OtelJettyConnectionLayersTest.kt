package misk.opentelemetry.jetty

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.io.ssl.SslConnection
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Request as JettyRequest
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.junit.jupiter.api.Test

/** Connector stacks match Misk's JettyService: TLS negotiates through ALPN, and cleartext HTTP/2 upgrades. */
class OtelJettyConnectionLayersTest {
  private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
  private val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
  private val clientCertificates =
    HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()

  @Test
  fun `TLS HTTP 1 socket counts once with encrypted transport bytes`() {
    assertOneSocketWithTransportBytes(Protocol.HTTP_1_1)
  }

  @Test
  fun `TLS HTTP 2 socket counts once with encrypted transport bytes`() {
    assertOneSocketWithTransportBytes(Protocol.HTTP_2)
  }

  @Test
  fun `cleartext HTTP 2 upgrade counts one socket`() {
    withServer(cleartext = true) { port, metrics, _ ->
      val client = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()
      repeat(2) { get(client, "http://localhost:$port/") }
      client.connectionPool.evictAll()

      val closed = awaitSocketsClosed(metrics)
      assertThat(closed.sum("jetty.connections.accepted")).isEqualTo(1)
      assertThat(closed.durationCount()).isEqualTo(1)
    }
  }

  private fun assertOneSocketWithTransportBytes(protocol: Protocol) {
    withServer(cleartext = false) { port, metrics, tlsBytes ->
      val client =
        OkHttpClient.Builder()
          .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
          .protocols(if (protocol == Protocol.HTTP_2) listOf(Protocol.HTTP_2, Protocol.HTTP_1_1) else listOf(protocol))
          .build()
      repeat(2) { assertThat(get(client, "https://localhost:$port/")).isEqualTo(protocol) }
      client.connectionPool.evictAll()

      val closed = awaitSocketsClosed(metrics)
      assertThat(closed.sum("jetty.connections.accepted")).isEqualTo(1)
      assertThat(closed.durationCount()).isEqualTo(1)
      assertThat(tlsBytes.received.get()).isPositive()
      assertThat(closed.sum("jetty.connection.bytes.received")).isEqualTo(tlsBytes.received.get())
      assertThat(closed.sum("jetty.connection.bytes.sent")).isEqualTo(tlsBytes.sent.get())
    }
  }

  private class TlsBytes : Connection.Listener {
    val received = AtomicLong()
    val sent = AtomicLong()

    override fun onOpened(connection: Connection) = Unit

    override fun onClosed(connection: Connection) {
      if (connection !is SslConnection) return
      received.addAndGet(connection.bytesIn)
      sent.addAndGet(connection.bytesOut)
    }
  }

  private fun withServer(cleartext: Boolean, block: (Int, InMemoryMetricReader, TlsBytes) -> Unit) {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val server = Server()
    val config = HttpConfiguration()
    val connector =
      if (cleartext) {
        ServerConnector(server, HttpConnectionFactory(config), HTTP2CServerConnectionFactory(config))
      } else {
        val secure = HttpConfiguration(config).apply { addCustomizer(SecureRequestCustomizer()) }
        val alpn = ALPNServerConnectionFactory("h2", "http/1.1").apply { defaultProtocol = "http/1.1" }
        val tls = SslContextFactory.Server().apply { sslContext = serverCertificates.sslContext() }
        ServerConnector(
          server,
          SslConnectionFactory(tls, alpn.protocol),
          alpn,
          HTTP2ServerConnectionFactory(secure),
          HttpConnectionFactory(secure),
        )
      }
    val tlsBytes = TlsBytes()
    connector.addBean(tlsBytes)
    server.addConnector(connector)
    val statistics = StatisticsHandler().apply { handler = OkHandler() }
    server.handler = statistics
    server.start()
    val service = OtelJettyConnectionMetricsService(telemetry, statistics, "test")
    try {
      service.startAsync().awaitRunning()
      block(connector.localPort, reader, tlsBytes)
    } finally {
      service.stopAsync().awaitTerminated()
      server.stop()
      provider.close()
    }
  }

  private class OkHandler : AbstractHandler() {
    override fun handle(
      target: String,
      baseRequest: JettyRequest,
      request: HttpServletRequest,
      response: HttpServletResponse,
    ) {
      response.status = 200
      response.writer.write("ok")
      baseRequest.isHandled = true
    }
  }

  private fun get(client: OkHttpClient, url: String): Protocol =
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
      assertThat(response.code).isEqualTo(200)
      response.body.string()
      response.protocol
    }

  /** Jetty sees the client's close asynchronously, so wait until no socket is active. */
  private fun awaitSocketsClosed(reader: InMemoryMetricReader): Map<String, MetricData> {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (true) {
      val metrics = reader.collectAllMetrics().associateBy { it.name }
      val active = metrics["jetty.connection.active"]?.longSumData?.points?.sumOf { it.value }
      if (active == 0L && metrics.durationCount() > 0) return metrics
      check(System.nanoTime() < deadline) { "sockets still active: $metrics" }
      Thread.sleep(50)
    }
  }

  private fun Map<String, MetricData>.sum(name: String): Long = getValue(name).longSumData.points.sumOf { it.value }

  private fun Map<String, MetricData>.durationCount(): Long =
    get("jetty.connection.duration")?.histogramData?.points?.sumOf { it.count } ?: 0
}
