package misk.clustering.zookeeper

import com.google.inject.Key
import com.google.inject.Provides
import misk.ServiceModule
import misk.clustering.ClusterService
import misk.clustering.lease.LeaseManager
import misk.clustering.weights.ClusterWeightService
import misk.concurrent.ExecutorServiceModule
import misk.inject.KAbstractModule
import misk.tasks.RepeatedTaskQueue
import misk.zookeeper.CuratorFrameworkModule
import misk.zookeeper.ZkService
import java.time.Clock
import java.util.concurrent.ExecutorService
import javax.inject.Singleton


class ZkLeaseCommonModule(private val config: ZookeeperConfig) : KAbstractModule() {
  override fun configure() {
    install(ZkLeaseManagerModule())
    install(ExecutorServiceModule.withFixedThreadPool(ForZkLease::class, "zk-lease-poller", 1))
    install(CuratorFrameworkModule(config, ForZkLease::class))
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
 * Common bindings between [ZkLeaseCommonModule] and [ZkLeaseTestModule].
 */
internal class ZkLeaseManagerModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ZkLeaseManager>()
        .dependsOn<ZkService>(ForZkLease::class)
        .dependsOn<ClusterWeightService>()
        .dependsOn<ClusterService>())
    install(ServiceModule<RepeatedTaskQueue>(ForZkLease::class))
    bind<LeaseManager>().to<ZkLeaseManager>()
  }
}
