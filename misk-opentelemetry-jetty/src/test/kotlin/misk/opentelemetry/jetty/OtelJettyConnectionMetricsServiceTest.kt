package misk.opentelemetry.jetty

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.unixdomain.server.UnixDomainServerConnector
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OtelJettyConnectionMetricsServiceTest {
  @Test
  fun `polling and closing overlapping connections retains all bytes and messages exactly once`() {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val meter = telemetry.getMeter("connection-test")
    val listener =
      OtelJettyConnectionListener(
        durationHistogram = meter.histogramBuilder("jetty.connection.duration").setUnit("s").build(),
        activeCounter = meter.upDownCounterBuilder("jetty.connection.active").build(),
        bytesReceivedCounter = meter.counterBuilder("jetty.connection.bytes.received").build(),
        bytesSentCounter = meter.counterBuilder("jetty.connection.bytes.sent").build(),
        acceptedCounter = meter.counterBuilder("jetty.connections.accepted").build(),
        messagesReceivedCounter = meter.counterBuilder("jetty.connection.messages.received").build(),
        messagesSentCounter = meter.counterBuilder("jetty.connection.messages.sent").build(),
        attributes = Attributes.empty(),
      )
    try {
      val shortLived = connection()
      val keepAlive = connection()
      listener.onOpened(shortLived)
      listener.onOpened(keepAlive)
      whenever(shortLived.bytesIn).thenReturn(10)
      whenever(shortLived.bytesOut).thenReturn(20)
      whenever(shortLived.messagesIn).thenReturn(1)
      whenever(shortLived.messagesOut).thenReturn(2)
      listener.refreshMetrics()
      whenever(shortLived.bytesIn).thenReturn(15)
      whenever(shortLived.bytesOut).thenReturn(25)
      whenever(shortLived.messagesIn).thenReturn(2)
      whenever(shortLived.messagesOut).thenReturn(3)
      listener.onClosed(shortLived)

      whenever(keepAlive.bytesIn).thenReturn(30)
      whenever(keepAlive.bytesOut).thenReturn(40)
      whenever(keepAlive.messagesIn).thenReturn(3)
      whenever(keepAlive.messagesOut).thenReturn(4)
      listener.refreshMetrics()
      listener.refreshMetrics()
      whenever(keepAlive.bytesIn).thenReturn(50)
      whenever(keepAlive.bytesOut).thenReturn(60)
      whenever(keepAlive.messagesIn).thenReturn(5)
      whenever(keepAlive.messagesOut).thenReturn(6)
      listener.onClosed(keepAlive)

      val metrics = reader.collectAllMetrics().associateBy { it.name }
      assertThat(metrics.getValue("jetty.connection.bytes.received").longSumData.points.single().value).isEqualTo(65)
      assertThat(metrics.getValue("jetty.connection.bytes.sent").longSumData.points.single().value).isEqualTo(85)
      assertThat(metrics.getValue("jetty.connection.messages.received").longSumData.points.single().value).isEqualTo(7)
      assertThat(metrics.getValue("jetty.connection.messages.sent").longSumData.points.single().value).isEqualTo(9)
      assertThat(metrics.getValue("jetty.connections.accepted").longSumData.points.single().value).isEqualTo(2)
      assertThat(metrics.getValue("jetty.connection.active").longSumData.points.single().value).isZero()
      assertThat(metrics.getValue("jetty.connection.duration").histogramData.points.single().count).isEqualTo(2)
    } finally {
      provider.close()
    }
  }

  @Test
  fun `active connections sum across connectors that share attributes`() {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = OpenTelemetrySdk.builder().setMeterProvider(provider).build().getMeter("connection-test")
    val active = meter.upDownCounterBuilder("jetty.connection.active").build()
    fun listener() =
      OtelJettyConnectionListener(
        durationHistogram = meter.histogramBuilder("jetty.connection.duration").build(),
        activeCounter = active,
        bytesReceivedCounter = meter.counterBuilder("jetty.connection.bytes.received").build(),
        bytesSentCounter = meter.counterBuilder("jetty.connection.bytes.sent").build(),
        acceptedCounter = meter.counterBuilder("jetty.connections.accepted").build(),
        messagesReceivedCounter = meter.counterBuilder("jetty.connection.messages.received").build(),
        messagesSentCounter = meter.counterBuilder("jetty.connection.messages.sent").build(),
        attributes = Attributes.empty(),
      )
    try {
      val first = listener()
      val second = listener()
      val closed = connection()
      first.onOpened(closed)
      first.onOpened(connection())
      second.onOpened(connection())
      first.onClosed(closed)
      // Closing an unknown connection must not decrement another connector's count.
      second.onClosed(connection())

      val activePoint = reader.collectAllMetrics().single { it.name == "jetty.connection.active" }
      assertThat(activePoint.longSumData.points.single().value).isEqualTo(2)
    } finally {
      provider.close()
    }
  }

  @Test
  fun `shutdown records an open connection once and preserves its final traffic before Jetty closes it`() {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = OpenTelemetrySdk.builder().setMeterProvider(provider).build().getMeter("connection-test")
    val listener =
      OtelJettyConnectionListener(
        durationHistogram = meter.histogramBuilder("jetty.connection.duration").setUnit("s").build(),
        activeCounter = meter.upDownCounterBuilder("jetty.connection.active").build(),
        bytesReceivedCounter = meter.counterBuilder("jetty.connection.bytes.received").build(),
        bytesSentCounter = meter.counterBuilder("jetty.connection.bytes.sent").build(),
        acceptedCounter = meter.counterBuilder("jetty.connections.accepted").build(),
        messagesReceivedCounter = meter.counterBuilder("jetty.connection.messages.received").build(),
        messagesSentCounter = meter.counterBuilder("jetty.connection.messages.sent").build(),
        attributes = Attributes.empty(),
      )
    try {
      val connection = connection()
      listener.onOpened(connection)
      whenever(connection.bytesIn).thenReturn(2)
      whenever(connection.bytesOut).thenReturn(3)
      whenever(connection.messagesIn).thenReturn(1)
      whenever(connection.messagesOut).thenReturn(1)
      listener.refreshMetrics()

      whenever(connection.bytesIn).thenReturn(10)
      whenever(connection.bytesOut).thenReturn(14)
      whenever(connection.messagesIn).thenReturn(2)
      whenever(connection.messagesOut).thenReturn(3)
      listener.stopObserving()

      val atShutdown = reader.collectAllMetrics().associateBy { it.name }
      assertThat(atShutdown.getValue("jetty.connection.bytes.received").longSumData.points.single().value).isEqualTo(10)
      assertThat(atShutdown.getValue("jetty.connection.bytes.sent").longSumData.points.single().value).isEqualTo(14)
      assertThat(atShutdown.getValue("jetty.connection.messages.received").longSumData.points.single().value)
        .isEqualTo(2)
      assertThat(atShutdown.getValue("jetty.connection.messages.sent").longSumData.points.single().value).isEqualTo(3)
      assertThat(atShutdown.getValue("jetty.connection.duration").histogramData.points.single().count).isEqualTo(1)
      assertThat(atShutdown.getValue("jetty.connection.active").longSumData.points.single().value).isZero()

      whenever(connection.bytesIn).thenReturn(20)
      whenever(connection.bytesOut).thenReturn(24)
      whenever(connection.messagesIn).thenReturn(4)
      whenever(connection.messagesOut).thenReturn(5)
      listener.onClosed(connection)
      listener.onOpened(connection())
      listener.refreshMetrics()
      val afterClose = reader.collectAllMetrics().associateBy { it.name }
      assertThat(afterClose.getValue("jetty.connection.bytes.received").longSumData.points.single().value).isEqualTo(10)
      assertThat(afterClose.getValue("jetty.connection.bytes.sent").longSumData.points.single().value).isEqualTo(14)
      assertThat(afterClose.getValue("jetty.connection.messages.received").longSumData.points.single().value)
        .isEqualTo(2)
      assertThat(afterClose.getValue("jetty.connection.messages.sent").longSumData.points.single().value).isEqualTo(3)
      assertThat(afterClose.getValue("jetty.connection.duration").histogramData.points.single().count).isEqualTo(1)
      assertThat(afterClose.getValue("jetty.connections.accepted").longSumData.points.single().value).isEqualTo(1)
      assertThat(afterClose.getValue("jetty.connection.active").longSumData.points.single().value).isZero()
    } finally {
      provider.close()
    }
  }

  @Test
  fun `service observes HTTP but not health connectors and removes its listener on shutdown`() {
    val provider = SdkMeterProvider.builder().build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val server = Server()
    val statistics = StatisticsHandler()
    val connector = ServerConnector(server).apply { name = "http" }
    val healthConnector = ServerConnector(server).apply { name = "health" }
    server.addConnector(connector)
    server.addConnector(healthConnector)
    server.handler = statistics
    val first = OtelJettyConnectionMetricsService(telemetry, statistics, "test")
    try {
      first.startAsync().awaitRunning()
      assertThat(connector.getBeans(OtelJettyConnectionListener::class.java)).hasSize(1)
      assertThat(healthConnector.getBeans(OtelJettyConnectionListener::class.java)).isEmpty()
      first.stopAsync().awaitTerminated()
      assertThat(connector.getBeans(OtelJettyConnectionListener::class.java)).isEmpty()

      val second = OtelJettyConnectionMetricsService(telemetry, statistics, "test")
      second.startAsync().awaitRunning()
      try {
        assertThat(connector.getBeans(OtelJettyConnectionListener::class.java)).hasSize(1)
        assertThat(healthConnector.getBeans(OtelJettyConnectionListener::class.java)).isEmpty()
      } finally {
        second.stopAsync().awaitTerminated()
      }
      assertThat(connector.getBeans(OtelJettyConnectionListener::class.java)).isEmpty()
    } finally {
      if (first.isRunning) first.stopAsync().awaitTerminated()
      provider.close()
    }
  }

  @Test
  fun `service observes every Unix socket by default and labels each with its path`() {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val server = Server()
    val statistics = StatisticsHandler()
    val first = UnixDomainServerConnector(server).apply { unixDomainPath = Path.of("/tmp/first.sock") }
    val second = UnixDomainServerConnector(server).apply { unixDomainPath = Path.of("/tmp/second.sock") }
    server.addConnector(first)
    server.addConnector(second)
    server.handler = statistics
    val service = OtelJettyConnectionMetricsService(telemetry, statistics, "test")
    try {
      service.startAsync().awaitRunning()
      first.getBeans(OtelJettyConnectionListener::class.java).single().onOpened(connection())
      second.getBeans(OtelJettyConnectionListener::class.java).single().onOpened(connection())

      val active = reader.collectAllMetrics().single { it.name == "jetty.connection.active" }
      assertThat(active.longSumData.points.map { it.attributes.get(LOCAL_ADDRESS) to it.value })
        .containsExactlyInAnyOrder("/tmp/first.sock" to 1L, "/tmp/second.sock" to 1L)
    } finally {
      service.stopAsync().awaitTerminated()
      provider.close()
    }
  }

  @Test
  fun `service observes only the selected Unix sockets`() {
    val provider = SdkMeterProvider.builder().build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val server = Server()
    val statistics = StatisticsHandler()
    val live = UnixDomainServerConnector(server).apply { unixDomainPath = Path.of("/tmp/live.sock") }
    val idle = UnixDomainServerConnector(server).apply { unixDomainPath = Path.of("/tmp/idle.sock") }
    server.addConnector(live)
    server.addConnector(idle)
    server.handler = statistics
    val service =
      OtelJettyConnectionMetricsService(
        telemetry,
        statistics,
        "test",
        observedUnixDomainSockets = setOf("/tmp/live.sock"),
      )
    try {
      service.startAsync().awaitRunning()
      assertThat(live.getBeans(OtelJettyConnectionListener::class.java)).hasSize(1)
      assertThat(idle.getBeans(OtelJettyConnectionListener::class.java)).isEmpty()
    } finally {
      service.stopAsync().awaitTerminated()
      provider.close()
    }
  }

  private companion object {
    fun connection(endPoint: EndPoint = mock()): Connection =
      mock<Connection>().also { whenever(it.endPoint).thenReturn(endPoint) }

    val LOCAL_ADDRESS: AttributeKey<String> = AttributeKey.stringKey("network.local.address")
  }
}
