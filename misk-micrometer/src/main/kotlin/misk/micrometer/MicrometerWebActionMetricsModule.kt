package misk.micrometer

import com.google.inject.multibindings.Multibinder
import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor

/**
 * Installs [MicrometerWebActionMetricsInterceptor] to record HTTP request metrics via Micrometer.
 *
 * This module should be installed alongside [MicrometerModule] and a backend-specific module like
 * [MicrometerPrometheusModule].
 *
 * Note: Do not install this alongside the legacy MetricsInterceptor to avoid duplicate metrics.
 *
 * Example:
 * ```
 * install(MicrometerModule())
 * install(MicrometerPrometheusModule())
 * install(MicrometerWebActionMetricsModule())
 * ```
 */
class MicrometerWebActionMetricsModule : KAbstractModule() {
  override fun configure() {
    val networkInterceptorBinder = Multibinder.newSetBinder(binder(), NetworkInterceptor.Factory::class.java)
    networkInterceptorBinder.addBinding().to(MicrometerWebActionMetricsInterceptor.Factory::class.java)
  }
}
