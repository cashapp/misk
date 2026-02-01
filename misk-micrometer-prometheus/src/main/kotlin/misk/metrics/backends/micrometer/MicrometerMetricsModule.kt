package misk.metrics.backends.micrometer

import com.google.inject.Provider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton

/**
 * Guice module that binds a Micrometer-backed implementation of Misk v2 Metrics.
 *
 * This module uses Micrometer with a Prometheus backend instead of the raw Prometheus client. It implements the same
 * misk.metrics.v2.Metrics interface, allowing users to swap this module for MetricsModule without changing any
 * callsites or imports.
 *
 * The implementation provides a CollectorRegistry from Micrometer's PrometheusMeterRegistry, which allows both
 * Micrometer meters and direct Prometheus metrics to coexist and be scraped from the same endpoint.
 *
 * Usage:
 * ```
 * // Instead of:
 * install(MetricsModule())
 *
 * // Use:
 * install(MicrometerMetricsModule())
 *
 * // Or with a provided registry:
 * val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
 * install(MicrometerMetricsModule(registry))
 * ```
 *
 * All existing code using `@Inject lateinit var metrics: misk.metrics.v2.Metrics` will continue to work without
 * changes.
 *
 * @param prometheusMeterRegistry Optional PrometheusMeterRegistry to use. If null, a new one will be created.
 */
class MicrometerMetricsModule(private val prometheusMeterRegistry: PrometheusMeterRegistry? = null) :
  KAbstractModule() {
  override fun configure() {
    if (prometheusMeterRegistry != null) {
      bind<PrometheusMeterRegistry>().toInstance(prometheusMeterRegistry)
    } else {
      bind<PrometheusMeterRegistry>().toProvider(PrometheusMeterRegistryProvider::class.java).asSingleton()
    }
    bind<MeterRegistry>().toProvider(MeterRegistryProvider::class.java).asSingleton()
    bind<CollectorRegistry>().toProvider(CollectorRegistryProvider::class.java).asSingleton()
    bind<misk.metrics.Metrics>().toProvider(MetricsProvider::class.java).asSingleton()
    bind<misk.metrics.v2.Metrics>().toProvider(V2MetricsProvider::class.java).asSingleton()
  }

  internal class PrometheusMeterRegistryProvider @Inject constructor() : Provider<PrometheusMeterRegistry> {
    override fun get(): PrometheusMeterRegistry {
      return PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }
  }

  internal class MeterRegistryProvider
  @Inject
  constructor(private val prometheusMeterRegistry: PrometheusMeterRegistry) : Provider<MeterRegistry> {
    override fun get(): MeterRegistry = prometheusMeterRegistry
  }

  internal class CollectorRegistryProvider
  @Inject
  constructor(private val prometheusMeterRegistry: PrometheusMeterRegistry) : Provider<CollectorRegistry> {
    override fun get(): CollectorRegistry {
      return prometheusMeterRegistry.prometheusRegistry
    }
  }

  internal class V2MetricsProvider @Inject constructor(private val collectorRegistry: CollectorRegistry) :
    Provider<misk.metrics.v2.Metrics> {
    override fun get(): misk.metrics.v2.Metrics {
      return misk.metrics.v2.Metrics.factory(collectorRegistry)
    }
  }

  internal class MetricsProvider @Inject constructor(private val v2Metrics: misk.metrics.v2.Metrics) :
    Provider<misk.metrics.Metrics> {
    override fun get(): misk.metrics.Metrics {
      return misk.metrics.Metrics.factory(v2Metrics)
    }
  }
}
