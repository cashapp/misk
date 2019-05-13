package misk.clustering.zookeeper

import misk.inject.KAbstractModule
import misk.zookeeper.FixedEnsembleProviderModule

/**
 * Binds a [LeaseManager] that uses Zookeeper.
 */
class ZkLeaseModule(private val config: ZookeeperConfig) : KAbstractModule() {
  override fun configure() {
    install(ZkLeaseCommonModule(config))
    install(FixedEnsembleProviderModule(config, ForZkLease::class))
  }
}