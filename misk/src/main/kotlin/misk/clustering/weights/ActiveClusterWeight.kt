package misk.clustering.weights

import com.google.common.util.concurrent.AbstractIdleService
import misk.ServiceModule
import misk.inject.KAbstractModule

/**
 * A static [ClusterWeightProvider] that always returns 100
 */
class ActiveClusterWeight : ClusterWeightProvider {

  override fun get(): Int {
    return 100
  }
}

internal class NoOpClusterWeightServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ClusterWeightService>())
    bind<ClusterWeightService>().toInstance(object : ClusterWeightService, AbstractIdleService() {
      override fun startUp() {
      }

      override fun shutDown() {
      }
    })
  }
}

/**
 * Provides an [ActiveClusterWeight]
 */
class ActiveClusterWeightModule : KAbstractModule() {
  override fun configure() {
    bind<ClusterWeightProvider>().to<ActiveClusterWeight>()
    install(NoOpClusterWeightServiceModule())
  }
}
