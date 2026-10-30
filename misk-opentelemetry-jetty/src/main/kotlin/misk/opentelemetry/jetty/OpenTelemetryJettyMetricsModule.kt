package misk.opentelemetry.jetty

import com.google.inject.Provides
import com.google.inject.multibindings.OptionalBinder
import io.opentelemetry.api.OpenTelemetry
import jakarta.inject.Singleton
import java.util.Optional
import misk.ServiceModule
import misk.inject.KInstallOnceModule
import misk.web.jetty.JettyService
import misk.web.jetty.MeasuredThreadPool
import org.eclipse.jetty.server.handler.StatisticsHandler

/**
 * Install only when the Misk web configuration enables Jetty. Without an [OpenTelemetry] binding (for example, a test
 * harness that does not install [misk.opentelemetry.OpenTelemetryModule]) the observer does nothing.
 */
class OpenTelemetryJettyMetricsModule : KInstallOnceModule() {
  override fun configure() {
    OptionalBinder.newOptionalBinder(binder(), OpenTelemetry::class.java)
    install(ServiceModule<OtelJettyMetricsService>().dependsOn<JettyService>())
  }

  @Provides
  @Singleton
  fun otelJettyMetricsService(
    openTelemetry: Optional<OpenTelemetry>,
    statisticsHandler: StatisticsHandler,
    threadPool: MeasuredThreadPool,
  ): OtelJettyMetricsService =
    OtelJettyMetricsService(openTelemetry.orElse(OpenTelemetry.noop()), statisticsHandler, threadPool)
}
