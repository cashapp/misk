package misk.clustering.zookeeper

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.clustering.lease.LeaseManager
import misk.inject.KAbstractModule
import org.apache.curator.framework.CuratorFramework

class ZookeeperModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<ZkService>()
    multibind<Service>().to<ZkLeaseManager>()
    bind<LeaseManager>().to<ZkLeaseManager>()
    bind<CuratorFramework>().toProvider(CuratorFrameworkProvider::class.java)
  }

  companion object {
    /** @property Key<*> The key of the service which manages the zk connection, for service dependencies */
    val serviceKey: Key<*> = Key.get(ZkService::class.java) as Key<*>

    /** @property Key<*> the Key of the lease manager service */
    val leaseManagerKey: Key<*> = Key.get(ZkLeaseManager::class.java)
  }
}