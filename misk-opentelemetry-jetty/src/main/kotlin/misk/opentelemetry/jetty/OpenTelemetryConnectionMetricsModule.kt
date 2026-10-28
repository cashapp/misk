package misk.opentelemetry.jetty

import com.google.inject.Provides
import com.google.inject.multibindings.OptionalBinder
import io.opentelemetry.api.OpenTelemetry
import jakarta.inject.Singleton
import java.util.Optional
import misk.ServiceModule
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.web.jetty.JettyService
import org.eclipse.jetty.server.handler.StatisticsHandler

/**
 * Installs Jetty connection-level OpenTelemetry metrics (bytes, messages, accepted/active count, duration) via
 * [OtelJettyConnectionMetricsService]. [JettyOpenTelemetryModule] installs this when Jetty is enabled and metrics are
 * exporting; services may also opt in explicitly. Do not install with Jetty disabled, since this service requires a
 * [StatisticsHandler].
 *
 * [observedUnixDomainSockets] limits which Unix domain sockets are observed, by path; `null` observes every socket Misk
 * listens on. Installing the module more than once is safe with the same filter; installs with different filters
 * conflict, so injector creation fails instead of silently keeping one of them.
 *
 * Prerequisites:
 * - An [OpenTelemetry] binding (see [misk.opentelemetry.OpenTelemetryModule]). Injectors without one, such as test
 *   harnesses, still resolve; the observer then does nothing.
 * - The `@AppName` binding must be available (standard in Misk services)
 * - The misk web stack with Jetty must be installed ([JettyService] and `StatisticsHandler` bindings)
 */
class OpenTelemetryConnectionMetricsModule
@JvmOverloads
constructor(private val observedUnixDomainSockets: Set<String>? = null) : KAbstractModule() {
  override fun configure() {
    OptionalBinder.newOptionalBinder(binder(), OpenTelemetry::class.java)
    install(ServiceModule<OtelJettyConnectionMetricsService>().dependsOn<JettyService>())
  }

  @Provides
  @Singleton
  internal fun otelJettyConnectionMetricsService(
    openTelemetry: Optional<OpenTelemetry>,
    statisticsHandler: StatisticsHandler,
    @AppName appName: String,
  ): OtelJettyConnectionMetricsService =
    OtelJettyConnectionMetricsService(
      openTelemetry.orElse(OpenTelemetry.noop()),
      statisticsHandler,
      appName,
      observedUnixDomainSockets,
    )

  override fun equals(other: Any?): Boolean =
    other is OpenTelemetryConnectionMetricsModule && other.observedUnixDomainSockets == observedUnixDomainSockets

  override fun hashCode(): Int = javaClass.hashCode() * 31 + observedUnixDomainSockets.hashCode()
}
