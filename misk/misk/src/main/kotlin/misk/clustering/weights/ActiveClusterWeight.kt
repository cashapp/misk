package misk.clustering.weights

import misk.inject.KAbstractModule

/**
 * A static [ClusterWeightProvider] that always returns 100
 */
class ActiveClusterWeight : ClusterWeightProvider {

  override fun get(): Int {
    return 100
  }
}

/**
 * Provides an [ActiveClusterWeight]
 */
class ActiveClusterWeightModule : KAbstractModule() {
  override fun configure() {
    bind<ClusterWeightProvider>().to<ActiveClusterWeight>()
  }
}
