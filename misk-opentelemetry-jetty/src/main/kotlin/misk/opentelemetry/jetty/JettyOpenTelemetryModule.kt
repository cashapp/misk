package misk.opentelemetry.jetty

import misk.inject.KAbstractModule
import misk.opentelemetry.openTelemetryMetricsExporting

/**
 * Installs the Jetty-only OpenTelemetry observers ([OpenTelemetryJettyMetricsModule] and
 * [OpenTelemetryConnectionMetricsModule]) when Jetty is enabled and the process-wide SDK exports metrics (see
 * [openTelemetryMetricsExporting]). Otherwise the observers would poll active connections every 500ms just to record
 * into no-op meters. Legacy Prometheus Jetty collectors are unaffected.
 *
 * @param observedUnixDomainSockets limits which Unix domain sockets get connection metrics, by path; `null` observes
 *   every socket Misk listens on
 */
class JettyOpenTelemetryModule
@JvmOverloads
constructor(
  private val jettyEnabled: Boolean,
  private val observedUnixDomainSockets: Set<String>? = null,
  private val metricsExporting: Boolean = openTelemetryMetricsExporting(),
) : KAbstractModule() {
  override fun configure() {
    if (!jettyEnabled || !metricsExporting) return
    install(OpenTelemetryJettyMetricsModule())
    install(OpenTelemetryConnectionMetricsModule(observedUnixDomainSockets))
  }
}
