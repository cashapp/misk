package misk.metrics.backends.prometheus

import misk.ServiceModule
import misk.inject.KAbstractModule

/**
 * Exposes prometheus metrics over a dedicated port. Allows internal metrics to be exposed via a k8s
 * ClusterIP address, where they can be scraped by a cluster local Prometheus server without also
 * exposing them to the outside world via the port bound to the service load balancer. If using
 * the prometheus operator, one would generally create a k8s ClusterIP service exporting the
 * metrics port, then a prometheus ServiceMonitor selecting that service via a label.
 */
class PrometheusMetricsModule(private val config: PrometheusConfig) : KAbstractModule() {
  override fun configure() {
    bind<PrometheusConfig>().toInstance(config)
    install(ServiceModule<PrometheusHttpService>())
  }
}