package misk.clustering.zookeeper

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Key
import com.google.inject.Provides
import misk.clustering.lease.LeaseManager
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.tasks.RepeatedTaskQueue
import misk.zookeeper.CuratorFrameworkProvider
import org.apache.curator.framework.CuratorFramework
import java.time.Clock
import java.util.concurrent.Executors
import javax.inject.Singleton

class ZookeeperModule(private val config: ZookeeperConfig) : KAbstractModule() {
  override fun configure() {
    bind<ZookeeperConfig>().toInstance(config)
    multibind<Service>().to<ZkService>()
    multibind<Service>().to<ZkLeaseManager>()
    multibind<Service>().to(RepeatedTaskQueue::class.toKey(ForZkLease::class)).asSingleton()
    bind<LeaseManager>().to<ZkLeaseManager>()
    bind<CuratorFramework>().toProvider(CuratorFrameworkProvider::class.java).asSingleton()
  }

  companion object {
    /** @property Key<*> The key of the service which manages the zk connection, for service dependencies */
    val serviceKey: Key<*> = Key.get(ZkService::class.java) as Key<*>

    /** @property Key<*> the Key of the lease manager service */
    val leaseManagerKey: Key<*> = Key.get(ZkLeaseManager::class.java)
  }

  @Provides @ForZkLease @Singleton
  fun provideTaskQueue(clock: Clock): RepeatedTaskQueue {
    return RepeatedTaskQueue(
        "zk-lease-poller",
        clock,
        Executors.newFixedThreadPool(1, ThreadFactoryBuilder()
            .setNameFormat("zk-lease-poller")
            .build()))
  }
}
