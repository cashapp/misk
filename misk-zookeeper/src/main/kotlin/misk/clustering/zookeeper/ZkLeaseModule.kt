package misk.clustering.zookeeper

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provides
import misk.clustering.lease.LeaseManager
import misk.concurrent.ExecutorServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.tasks.RepeatedTaskQueue
import misk.zookeeper.ZookeeperModule
import java.time.Clock
import java.util.concurrent.ExecutorService
import javax.inject.Singleton

/**
 * Binds a [LeaseManager] that uses Zookeeper.
 */
class ZkLeaseModule(private val config: ZookeeperConfig) : KAbstractModule() {
  override fun configure() {
    install(ZkLeaseCommonModule())
    install(ExecutorServiceModule.withFixedThreadPool(ForZkLease::class, "zk-lease-poller", 1))
    install(ZookeeperModule(config, ForZkLease::class))
  }

  companion object {
    /** @property Key<*> the Key of the lease manager service */
    val leaseManagerKey: Key<*> = Key.get(ZkLeaseManager::class.java)
  }

  @Provides @ForZkLease @Singleton
  fun provideTaskQueue(
    clock: Clock,
    @ForZkLease executorService: ExecutorService
  ): RepeatedTaskQueue {
    return RepeatedTaskQueue("zk-lease-poller", clock, executorService)
  }
}

/**
 * Common bindings between [ZkLeaseModule] and [ZkLeaseTestModule].
 */
internal class ZkLeaseCommonModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<ZkLeaseManager>()
    multibind<Service>().to(RepeatedTaskQueue::class.toKey(ForZkLease::class)).asSingleton()
    bind<LeaseManager>().to<ZkLeaseManager>()
  }
}
