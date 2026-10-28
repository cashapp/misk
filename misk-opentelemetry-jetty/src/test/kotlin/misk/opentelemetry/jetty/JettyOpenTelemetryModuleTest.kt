package misk.opentelemetry.jetty

import com.google.inject.AbstractModule
import com.google.inject.Binding
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.spi.Elements
import com.google.inject.util.Modules
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import java.util.Optional
import misk.config.AppName
import misk.opentelemetry.openTelemetryMetricsExporting
import misk.web.jetty.MeasuredQueuedThreadPool
import misk.web.jetty.MeasuredThreadPool
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.junit.jupiter.api.Test

class JettyOpenTelemetryModuleTest {
  @Test
  fun `installs Jetty observers only when Jetty is enabled and metrics are exporting`() {
    assertThat(observers(jettyEnabled = true, metricsExporting = true))
      .containsExactlyInAnyOrder(OtelJettyMetricsService::class.java, OtelJettyConnectionMetricsService::class.java)
    assertThat(observers(jettyEnabled = false, metricsExporting = true)).isEmpty()
    assertThat(observers(jettyEnabled = true, metricsExporting = false)).isEmpty()
  }

  @Test
  fun `metrics export matches autoconfigure defaults`() {
    // SDK: only "true" disables it; unset is enabled, as in autoconfigure.
    assertThat(openTelemetryMetricsExporting(sdkDisabled = null, metricsExporter = "otlp")).isTrue()
    assertThat(openTelemetryMetricsExporting(sdkDisabled = "TRUE", metricsExporter = "otlp")).isFalse()
    assertThat(openTelemetryMetricsExporting(sdkDisabled = "no", metricsExporter = "otlp")).isTrue()
    // Exporter: unset or blank defaults to otlp; otherwise the list must contain otlp.
    assertThat(openTelemetryMetricsExporting(sdkDisabled = null, metricsExporter = null)).isTrue()
    assertThat(openTelemetryMetricsExporting(sdkDisabled = null, metricsExporter = " ")).isTrue()
    assertThat(openTelemetryMetricsExporting(sdkDisabled = null, metricsExporter = " none , OTLP ")).isTrue()
    assertThat(openTelemetryMetricsExporting(sdkDisabled = null, metricsExporter = "logging")).isFalse()
    assertThat(openTelemetryMetricsExporting(sdkDisabled = null, metricsExporter = "none")).isFalse()
  }

  @Test
  fun `observers resolve without an OpenTelemetry binding and start as no-ops`() {
    val injector = Guice.createInjector(jettyPrerequisites(), JettyOpenTelemetryModule(true, metricsExporting = true))

    assertThat(injector.getInstance(optionalOpenTelemetry)).isEmpty()
    // The handler is attached to no server; a service that did any work would fail to start.
    val metrics = injector.getInstance(OtelJettyMetricsService::class.java)
    val connections = injector.getInstance(OtelJettyConnectionMetricsService::class.java)
    metrics.startAsync().awaitRunning()
    connections.startAsync().awaitRunning()
    metrics.stopAsync().awaitTerminated()
    connections.stopAsync().awaitTerminated()
  }

  @Test
  fun `observers use an SDK bound by override`() {
    val sdk = OpenTelemetrySdk.builder().build()
    val injector =
      Guice.createInjector(
        Modules.override(jettyPrerequisites(), JettyOpenTelemetryModule(true, metricsExporting = true))
          .with(
            object : AbstractModule() {
              override fun configure() {
                bind(OpenTelemetry::class.java).toInstance(sdk)
              }
            }
          )
      )

    assertThat(injector.getInstance(optionalOpenTelemetry)).containsSame(sdk)
  }

  private val optionalOpenTelemetry = Key.get(object : TypeLiteral<Optional<OpenTelemetry>>() {})

  private fun jettyPrerequisites() =
    object : AbstractModule() {
      override fun configure() {
        bind(StatisticsHandler::class.java).toInstance(StatisticsHandler())
        bind(MeasuredThreadPool::class.java).toInstance(MeasuredQueuedThreadPool(QueuedThreadPool()))
        bind(String::class.java).annotatedWith(AppName::class.java).toInstance("test-app")
      }
    }

  private fun observers(jettyEnabled: Boolean, metricsExporting: Boolean): Set<Class<*>> {
    val observerTypes = setOf(OtelJettyMetricsService::class.java, OtelJettyConnectionMetricsService::class.java)
    return Elements.getElements(JettyOpenTelemetryModule(jettyEnabled, metricsExporting = metricsExporting))
      .filterIsInstance<Binding<*>>()
      .map { (it.key as Key<*>).typeLiteral.rawType }
      .filter { it in observerTypes }
      .toSet()
  }
}
