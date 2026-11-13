package misk.opentelemetry.jetty

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.management.ObjectName
import misk.web.jetty.MeasuredQueuedThreadPool
import misk.web.jetty.MeasuredThreadPoolExecutor
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.jmx.MBeanContainer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.unixdomain.server.UnixDomainServerConnector
import org.eclipse.jetty.util.thread.ExecutorThreadPool
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.junit.jupiter.api.Test

class OtelJettyMetricsServiceTest {
  @Test
  fun `selectors of a pre-started filesystem Unix socket connector are registered and removed`() {
    val provider = SdkMeterProvider.builder().registerMetricReader(InMemoryMetricReader.create()).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val pool = QueuedThreadPool(20, 4)
    val server = Server(pool)
    server.addConnector(ServerConnector(server, 1, 1).apply { port = 0 })
    val socket = Files.createTempDirectory("uds").resolve("app.sock")
    // Misk starts filesystem Unix socket connectors before adding them, so Jetty treats them as unmanaged beans.
    val uds = UnixDomainServerConnector(server, 1, 1).apply { unixDomainPath = socket }
    server.handler = StatisticsHandler().apply { handler = ServletContextHandler() }
    var observer: OtelJettyMetricsService? = null
    try {
      uds.start()
      server.addConnector(uds)
      server.start()
      assertThat(server.isUnmanaged(uds)).isTrue()
      val running =
        OtelJettyMetricsService(telemetry, server.handler as StatisticsHandler, MeasuredQueuedThreadPool(pool))
      observer = running
      running.startAsync().awaitRunning()
      val mbeans = server.getBean(MBeanContainer::class.java).mBeanServer
      val selectors = ObjectName("org.eclipse.jetty.io:context=*,type=managedselector,id=*")
      // One selector each for the TCP and the Unix socket connector.
      assertThat(mbeans.queryNames(selectors, null)).hasSize(2)

      running.stopAsync().awaitTerminated()
      assertThat(uds.getBeans(MBeanContainer::class.java)).isEmpty()
    } finally {
      observer?.takeIf { it.isRunning }?.stopAsync()?.awaitTerminated()
      server.stop()
      uds.stop()
      provider.close()
    }
  }

  @Test
  fun `running Jetty11 exports upstream JMX metrics and custom request metrics without duplicates`() {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val pool = QueuedThreadPool(20, 4)
    val server = Server(pool)
    val connector = ServerConnector(server, 1, 1).apply { port = 0 }
    server.addConnector(connector)
    val context =
      ServletContextHandler(ServletContextHandler.SESSIONS).apply {
        contextPath = "/orders"
        addServlet(
          ServletHolder(
            object : HttpServlet() {
              override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                val session = request.getSession(true)
                if (request.requestURI.endsWith("/end")) session.invalidate()
                response.status = HttpServletResponse.SC_OK
              }
            }
          ),
          "/*",
        )
      }
    val statistics = StatisticsHandler().apply { handler = context }
    server.handler = statistics
    var observer: OtelJettyMetricsService? = null
    try {
      // The service starts after JettyService: MBeanContainer must register already-running children.
      server.start()
      val running = OtelJettyMetricsService(telemetry, statistics, MeasuredQueuedThreadPool(pool))
      observer = running
      running.startAsync().awaitRunning()
      val container = server.getBean(MBeanContainer::class.java)
      assertThat(container).isNotNull
      val mbeans = container.mBeanServer
      val threadBean = mbeans.queryNames(ObjectName("org.eclipse.jetty.util.thread:type=queuedthreadpool,id=*"), null)
      val selectorBeans =
        mbeans.queryNames(ObjectName("org.eclipse.jetty.io:context=*,type=managedselector,id=*"), null)
      val sessionBean =
        mbeans.queryNames(ObjectName("org.eclipse.jetty.server.session:context=*,type=sessionhandler,id=*"), null)
      assertThat(threadBean).hasSize(1)
      assertThat(selectorBeans).hasSize(1)
      assertThat(sessionBean).hasSize(1)
      assertThat(sessionBean.single().getKeyProperty("context")).isEqualTo("orders")
      assertThat(mbeans.getAttribute(threadBean.single(), "maxThreads")).isEqualTo(20)
      assertThat(mbeans.getAttribute(sessionBean.single(), "sessionsCreated")).isEqualTo(0)

      val nativeNames =
        setOf(
          "jetty.thread.count",
          "jetty.thread.limit",
          "jetty.thread.idle.count",
          "jetty.thread.busy.count",
          "jetty.thread.queue.size",
          "jetty.select.count",
          "jetty.session.created.count",
          "jetty.session.duration.sum",
        )
      var jmx = awaitJmxMetrics(reader, nativeNames)
      assertThat(jmx.getValue("jetty.thread.limit").longSumData.points.single().value).isEqualTo(20)
      assertThat(jmx.getValue("jetty.session.created.count").longSumData.points.single().value).isZero()
      assertThat(jmx.getValue("jetty.session.duration.sum").longSumData.points.single().value).isZero()
      assertThat(
          jmx
            .getValue("jetty.session.created.count")
            .longSumData
            .points
            .single()
            .attributes
            .get(AttributeKey.stringKey("jetty.context"))
        )
        .isEqualTo("orders")
      assertThat(jmx.getValue("jetty.select.count").longSumData.points.single().value).isGreaterThanOrEqualTo(0)

      val base = "http://localhost:${connector.localPort}/orders"
      val first = URL("$base/start").openConnection()
      first.getInputStream().use { it.read() }
      val cookie = first.getHeaderField("Set-Cookie").substringBefore(';')
      Thread.sleep(1_100)
      val last = URL("$base/end").openConnection()
      last.setRequestProperty("Cookie", cookie)
      last.getInputStream().use { it.read() }
      jmx = awaitJmxMetrics(reader, nativeNames)
      assertThat(context.sessionHandler.sessionsCreated).isEqualTo(1)
      assertThat(jmx.getValue("jetty.session.created.count").longSumData.points.single().value).isEqualTo(1)
      assertThat(context.sessionHandler.sessionTimeTotal).isGreaterThanOrEqualTo(1)
      assertThat(jmx.getValue("jetty.session.duration.sum").longSumData.points.single().value)
        .isEqualTo(context.sessionHandler.sessionTimeTotal)
      assertThat(
          jmx
            .getValue("jetty.session.duration.sum")
            .longSumData
            .points
            .single()
            .attributes
            .get(AttributeKey.stringKey("jetty.context"))
        )
        .isEqualTo("orders")
      assertThat(jmx.getValue("jetty.select.count").longSumData.points.single().value).isGreaterThan(0)
      assertThat((mbeans.getAttribute(selectorBeans.single(), "selectCount") as Number).toLong()).isGreaterThan(0)
      val observedThreads = jmx.getValue("jetty.thread.count").longSumData.points.single().value
      val observedIdle = jmx.getValue("jetty.thread.idle.count").longSumData.points.single().value
      val observedBusy = jmx.getValue("jetty.thread.busy.count").longSumData.points.single().value
      assertThat(observedThreads).isPositive()
      assertThat(observedIdle).isBetween(0, observedThreads)
      assertThat(observedBusy).isBetween(0, observedThreads)
      assertThat(jmx.getValue("jetty.thread.queue.size").longSumData.points.single().value).isGreaterThanOrEqualTo(0)

      val collected = reader.collectAllMetrics()
      assertThat(collected.filter { it.name in nativeNames }).hasSize(nativeNames.size)
      assertThat(collected.filter { it.name in nativeNames }.map { it.instrumentationScopeInfo.name }.toSet())
        .containsExactly("io.opentelemetry.jmx")
      val custom = collected.associateBy { it.name }
      assertThat(custom.getValue("jetty.requests").instrumentationScopeInfo.name).isEqualTo("misk.jetty")
      assertThat(custom.getValue("jetty.requests").longSumData.points.single().value).isEqualTo(2)
      assertThat(custom.getValue("jetty.dispatches").longSumData.points.single().value).isEqualTo(2)
      assertThat(
          custom
            .getValue("jetty.responses")
            .longSumData
            .points
            .single { it.attributes.get(AttributeKey.stringKey("jetty.response.status_class")) == "2xx" }
            .value
        )
        .isEqualTo(2)
      assertThat(custom.getValue("jetty.async.requests").longSumData.points.single().value).isZero()
      assertThat(custom.keys).doesNotContain("http.server.request.duration")

      running.stopAsync().awaitTerminated()
      assertThat(server.getBean(MBeanContainer::class.java)).isNull()
      assertThat(mbeans.queryNames(ObjectName("org.eclipse.jetty.*:*"), null)).isEmpty()
      // Servlet statistics stay registered with the SDK; JMX instruments close with the service.
      assertThat(reader.collectAllMetrics().map { it.name })
        .doesNotContainAnyElementsOf(nativeNames)
        .contains("jetty.requests")

      val restarted = OtelJettyMetricsService(telemetry, statistics, MeasuredQueuedThreadPool(pool))
      restarted.startAsync().awaitRunning()
      try {
        assertThat(server.getBean(MBeanContainer::class.java)).isNotSameAs(container)
        assertThat(
            awaitJmxMetrics(reader, nativeNames)
              .getValue("jetty.session.created.count")
              .longSumData
              .points
              .single()
              .value
          )
          .isEqualTo(1)
        assertThat(reader.collectAllMetrics().single { it.name == "jetty.requests" }.longSumData.points.single().value)
          .isEqualTo(2)
      } finally {
        restarted.stopAsync().awaitTerminated()
      }
      assertThat(reader.collectAllMetrics().map { it.name })
        .doesNotContainAnyElementsOf(nativeNames)
        .contains("jetty.requests")
    } finally {
      observer?.takeIf { it.isRunning }?.stopAsync()?.awaitTerminated()
      server.stop()
      provider.close()
    }
  }

  @Test
  fun `a reloaded app's new Jetty server carries totals on the process SDK`() {
    // With delta temporality the SDK forgets an asynchronous counter that a collection does not observe.
    val reader = InMemoryMetricReader.createDelta()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    fun requestsDelta() =
      reader.collectAllMetrics().single { it.name == "jetty.requests" }.longSumData.points.single().value
    try {
      // Each hot reload builds a new server whose statistics start at zero. Each server handles one request
      // before a collection and one after it, and a collection also runs while no service is running.
      repeat(2) {
        val pool = QueuedThreadPool(8, 2)
        val server = Server(pool)
        val connector = ServerConnector(server, 1, 1).apply { port = 0 }
        server.addConnector(connector)
        val context =
          ServletContextHandler().apply {
            addServlet(
              ServletHolder(
                object : HttpServlet() {
                  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                    response.status = HttpServletResponse.SC_OK
                  }
                }
              ),
              "/*",
            )
          }
        val statistics = StatisticsHandler().apply { handler = context }
        server.handler = statistics
        server.start()
        val service = OtelJettyMetricsService(telemetry, statistics, MeasuredQueuedThreadPool(pool))
        try {
          service.startAsync().awaitRunning()
          val url = URL("http://localhost:${connector.localPort}/")
          url.openConnection().getInputStream().use { it.read() }
          assertThat(requestsDelta()).isEqualTo(1)
          url.openConnection().getInputStream().use { it.read() }
        } finally {
          service.stopAsync().awaitTerminated()
          server.stop()
        }
        assertThat(requestsDelta()).isEqualTo(1)
      }
    } finally {
      provider.close()
    }
  }

  @Test
  fun `non queued Misk thread pool keeps only native thread observations`() {
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val telemetry = OpenTelemetrySdk.builder().setMeterProvider(provider).build()
    val executor = ThreadPoolExecutor(4, 10, 60, TimeUnit.SECONDS, SynchronousQueue())
    val server = Server(ExecutorThreadPool(executor))
    val connector = ServerConnector(server, 1, 1).apply { port = 0 }
    server.addConnector(connector)
    val context =
      ServletContextHandler().apply {
        contextPath = "/orders"
        addServlet(
          ServletHolder(
            object : HttpServlet() {
              override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                response.status = HttpServletResponse.SC_OK
              }
            }
          ),
          "/*",
        )
      }
    val statistics = StatisticsHandler().apply { handler = context }
    server.handler = statistics
    var observer: OtelJettyMetricsService? = null
    try {
      server.start()
      val running = OtelJettyMetricsService(telemetry, statistics, MeasuredThreadPoolExecutor(executor))
      observer = running
      running.startAsync().awaitRunning()
      val mbeans = server.getBean(MBeanContainer::class.java).mBeanServer
      assertThat(mbeans.queryNames(ObjectName("org.eclipse.jetty.util.thread:type=queuedthreadpool,id=*"), null))
        .isEmpty()
      URL("http://localhost:${connector.localPort}/orders/ok").openStream().use { it.read() }
      val jmx = awaitJmxMetrics(reader, setOf("jetty.select.count"))
      assertThat(jmx.keys)
        .doesNotContain(
          "jetty.thread.count",
          "jetty.thread.limit",
          "jetty.session.created.count",
          "jetty.session.duration.sum",
        )
      val observed = reader.collectAllMetrics().associateBy { it.name }
      assertThat(observed.getValue("jetty.thread.count").instrumentationScopeInfo.name).isEqualTo("misk.jetty")
      assertThat(observed.getValue("jetty.thread.limit").longSumData.points.single().value).isEqualTo(10)
      assertThat(reader.collectAllMetrics().count { it.name == "jetty.thread.count" }).isEqualTo(1)
      assertThat(observed.getValue("jetty.requests").longSumData.points.single().value).isEqualTo(1)
      running.stopAsync().awaitTerminated()
      val afterStop = reader.collectAllMetrics().map { it.name }
      assertThat(afterStop).noneMatch { it.startsWith("jetty.thread.") }.contains("jetty.requests")
    } finally {
      observer?.takeIf { it.isRunning }?.stopAsync()?.awaitTerminated()
      server.stop()
      provider.close()
    }
  }

  private fun awaitJmxMetrics(reader: InMemoryMetricReader, names: Set<String>): Map<String, MetricData> {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (System.nanoTime() < deadline) {
      val jmx =
        reader
          .collectAllMetrics()
          .filter { it.instrumentationScopeInfo.name == "io.opentelemetry.jmx" }
          .associateBy { it.name }
      if (jmx.keys.containsAll(names)) return jmx
      Thread.sleep(100)
    }
    val jmx =
      reader
        .collectAllMetrics()
        .filter { it.instrumentationScopeInfo.name == "io.opentelemetry.jmx" }
        .associateBy { it.name }
    assertThat(jmx.keys).containsAll(names)
    return jmx
  }
}
