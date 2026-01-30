package misk.micrometer.prometheus

import com.google.inject.Provider
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.inject.KAbstractModule

/**
 * Installs Micrometer Prometheus support for Misk applications.
 *
 * This module:
 * - Creates a [PrometheusMeterRegistry] that shares the same [PrometheusRegistry] as the existing misk-metrics
 *   Prometheus integration
 * - Adds the Prometheus registry to the composite meter registry
 * - Allows both legacy misk-metrics and new Micrometer metrics to be exposed at the same Prometheus endpoint
 *
 * Requires:
 * - [MicrometerModule] to be installed
 * - [MetricsModule] or [PrometheusMetricsServiceModule] from misk-metrics/misk-prometheus
 *
 * Example:
 * ```
 * install(MetricsModule())
 * install(PrometheusMetricsServiceModule())
 * install(MicrometerModule())
 * install(MicrometerPrometheusModule())
 * ```
 */
class MicrometerPrometheusModule : KAbstractModule() {
  override fun configure() {
    bind<PrometheusMeterRegistry>().toProvider(PrometheusMeterRegistryProvider::class.java).asEagerSingleton()
  }
}

/** Provider for the [PrometheusMeterRegistry] that shares the Prometheus [CollectorRegistry] with misk-metrics. */
@Singleton
private class PrometheusMeterRegistryProvider
@Inject
constructor(
  private val collectorRegistry: CollectorRegistry,
  private val compositeMeterRegistry: CompositeMeterRegistry,
  private val clock: Clock,
) : Provider<PrometheusMeterRegistry> {
  private val registry by lazy {
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, collectorRegistry, clock)
    compositeMeterRegistry.add(prometheusMeterRegistry)
    prometheusMeterRegistry
  }

  override fun get(): PrometheusMeterRegistry = registry
}
