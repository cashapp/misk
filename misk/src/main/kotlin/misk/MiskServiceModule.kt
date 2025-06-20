package misk

import misk.concurrent.ExecutorsModule
import misk.concurrent.SleeperModule
import misk.environment.RealEnvVarModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.jvm.JvmManagementFactoryModule
import misk.logging.MdcModule
import misk.metrics.backends.prometheus.PrometheusMetricsClientModule
import misk.moshi.MoshiModule
import misk.resources.ResourceLoaderModule
import misk.time.ClockModule
import misk.time.TickerModule
import misk.tokens.TokenGeneratorModule
import misk.web.metadata.MetadataModule
import misk.web.metadata.guice.GuiceMetadataProvider

/**
 * Install this module in real environments.
 *
 * The vast majority of Service bindings belong in [MiskCommonServiceModule], in order to share
 * with [MiskTestingServiceModule]. Only bindings that are not suitable for a unit testing
 * environment belong here.
 */
class MiskRealServiceModule @JvmOverloads constructor(
  private val serviceManagerConfig: ServiceManagerConfig = ServiceManagerConfig(),
) : KAbstractModule() {
  override fun configure() {
    install(ResourceLoaderModule(isReal = true))
    install(RealEnvVarModule())
    install(ClockModule())
    install(SleeperModule())
    install(TickerModule())
    install(TokenGeneratorModule())
    install(MiskCommonServiceModule(serviceManagerConfig))
  }
}

/**
 * This module has common bindings for all environments (both real and testing).
 */
class MiskCommonServiceModule @JvmOverloads constructor(
  private val serviceManagerConfig: ServiceManagerConfig = ServiceManagerConfig(),
  private val installMetrics: Boolean = true
) : KAbstractModule() {
  override fun configure() {
    binder().disableCircularProxies()
    binder().requireExactBindingAnnotations()
    install(MdcModule())
    install(ExecutorsModule())
    install(ServiceManagerModule(serviceManagerConfig))
    if (installMetrics) {
      install(PrometheusMetricsClientModule())
    }
    install(MoshiModule(useWireToRead = true, useWireToWrite = true))
    install(JvmManagementFactoryModule())
    // Initialize empty sets for our multibindings.
    newMultibinder<HealthCheck>()
    install(ServiceModule<ReadyService>())
    install(MetadataModule(GuiceMetadataProvider()))
  }
}
