package misk.metrics.backends.prometheus

import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.BufferPoolsExports
import io.prometheus.client.hotspot.ClassLoadingExports
import io.prometheus.client.hotspot.GarbageCollectorExports
import io.prometheus.client.hotspot.MemoryPoolsExports
import io.prometheus.client.hotspot.StandardExports
import io.prometheus.client.hotspot.ThreadExports
import io.prometheus.client.hotspot.VersionInfoExports
import misk.ServiceModule
import misk.inject.KAbstractModule
import javax.inject.Inject

/**
 * Exposes prometheus metrics over a dedicated port. Allows internal metrics to be exposed via a k8s
 * ClusterIP address, where they can be scraped by a cluster local Prometheus server without also
 * exposing them to the outside world via the port bound to the service load balancer. If using
 * the prometheus operator, one would generally create a k8s ClusterIP service exporting the
 * metrics port, then a prometheus ServiceMonitor selecting that service via a label.
 */
class PrometheusMetricsServiceModule(private val config: PrometheusConfig) : KAbstractModule() {
  override fun configure() {
    install(PrometheusMetricsClientModule())

    bind<PrometheusConfig>().toInstance(config)
    install(ServiceModule<PrometheusHttpService>())

    // For every Collector registered with a multibinding, configure it in the registry when the
    // injector is created.
    requestInjection(object : Any() {
      @Inject fun registerCollectors(
        registry: CollectorRegistry,
        collectors: @JvmSuppressWildcards Set<Collector>
      ) {
        collectors.forEach { registry.register(it) }
      }
    })

    // Bind collectors that are packaged with Prometheus.
    multibind<Collector>().toInstance(StandardExports())
    multibind<Collector>().toInstance(MemoryPoolsExports())
    multibind<Collector>().toInstance(BufferPoolsExports())
    multibind<Collector>().toInstance(ThreadExports())
    multibind<Collector>().toInstance(GarbageCollectorExports())
    multibind<Collector>().toInstance(ClassLoadingExports())
    multibind<Collector>().toInstance(VersionInfoExports())
  }
}
