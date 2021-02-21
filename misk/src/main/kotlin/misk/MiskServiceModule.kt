package misk

import misk.concurrent.ExecutorsModule
import misk.concurrent.SleeperModule
import misk.environment.RealEnvVarModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.metrics.backends.prometheus.PrometheusMetricsClientModule
import misk.moshi.MoshiModule
import misk.resources.ResourceLoaderModule
import misk.time.ClockModule
import misk.time.TickerModule
import misk.tokens.TokenGeneratorModule

/**
 * Install this module in real environments.
 *
 * The vast majority of Service bindings belong in [MiskCommonServiceModule], in order to share
 * with [MiskTestingServiceModule]. Only bindings that are not suitable for a unit testing
 * environment belong here.
 */
class MiskRealServiceModule : KAbstractModule() {
  override fun configure() {
    install(ResourceLoaderModule())
    install(RealEnvVarModule())
    install(ClockModule())
    install(SleeperModule())
    install(TickerModule())
    install(TokenGeneratorModule())
    install(MiskCommonServiceModule())
  }
}

/**
 * This module has common bindings for all environments (both real and testing).
 */
class MiskCommonServiceModule : KAbstractModule() {
  override fun configure() {
    binder().disableCircularProxies()
    binder().requireExactBindingAnnotations()
    install(ExecutorsModule())
    install(ServiceManagerModule())
    install(PrometheusMetricsClientModule())
    install(MoshiModule(useWireToRead = true))

    // Initialize empty sets for our multibindings.
    newMultibinder<HealthCheck>()
  }
}
