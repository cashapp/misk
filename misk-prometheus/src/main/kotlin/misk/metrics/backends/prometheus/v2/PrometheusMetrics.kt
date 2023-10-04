package misk.metrics.backends.prometheus.v2

import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics

/**
 * Accepts metrics and writes them to the Prometheus [CollectorRegistry].
 */
@Singleton
internal class PrometheusMetrics @Inject internal constructor(
  private val registry: CollectorRegistry
) : Metrics {
  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }

  override fun getRegistry() = registry
}
