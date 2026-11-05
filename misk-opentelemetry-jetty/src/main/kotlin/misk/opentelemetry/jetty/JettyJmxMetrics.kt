package misk.opentelemetry.jetty

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.jmx.JmxTelemetry
import javax.management.MBeanServer

/** Collects upstream Jetty JMX metrics using the application's existing SDK and a local MBean server. */
object JettyJmxMetrics {
  private val JETTY_SYSTEM = IncludeExclude.builder().setIncluded("jetty").build()

  // Upstream publishes every Jetty JMX metric as unstable, and unstable metrics are excluded unless opted in. Only the
  // Jetty system is loaded, so this enables exactly its metrics.
  private val ALL_METRICS = IncludeExclude.builder().setIncluded("*").build()

  @JvmStatic
  fun start(openTelemetry: OpenTelemetry, mbeanServer: MBeanServer): AutoCloseable {
    val telemetry =
      JmxTelemetry.builder(openTelemetry)
        .setInternalMetricsSystemFilter(JETTY_SYSTEM)
        .setInternalMetricsUnstableMetricsFilter(ALL_METRICS)
        .build()
    val connections = listOf(mbeanServer)
    return telemetry.start { connections }
  }
}
