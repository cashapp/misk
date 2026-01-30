package misk.metrics

import com.google.inject.Provider
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metrics.v3.Metrics as MetricsV3

/**
 * Binds v3.Metrics backed by Micrometer.
 *
 * Requires:
 * - [MicrometerModule] from misk-micrometer to provide the MeterRegistry
 * - [MicrometerPrometheusModule] from misk-micrometer-prometheus to export to shared Prometheus CollectorRegistry
 *
 * Example:
 * ```
 * install(MetricsModule()) // legacy v1/v2 + shared CollectorRegistry
 * install(MicrometerModule())
 * install(MicrometerPrometheusModule())
 * install(MetricsV3Module())
 * ```
 */
class MetricsV3Module : KAbstractModule() {
  override fun configure() {
    bind<MetricsV3>().toProvider(V3MetricsProvider::class.java).asSingleton()
  }

  internal class V3MetricsProvider @Inject constructor(private val meterRegistry: MeterRegistry) : Provider<MetricsV3> {
    override fun get(): MetricsV3 = MetricsV3.from(meterRegistry)
  }
}
