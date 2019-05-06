package misk.clustering.zookeeper

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import misk.clustering.fake.FakeClusterModule
import misk.clustering.lease.LeaseManager
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.tasks.DelayedTask
import misk.tasks.RepeatedTaskQueue
import misk.zookeeper.ZkTestModule
import java.time.Clock

internal class ZkLeaseTestModule : KAbstractModule() {
  override fun configure() {
    bind<String>().annotatedWith<AppName>().toInstance("my-app")
    install(FakeClusterModule())
    install(ZkTestModule(ForZkLease::class))

    // TODO: these are repeated
    multibind<Service>().to<ZkLeaseManager>()
    multibind<Service>().to(RepeatedTaskQueue::class.toKey(ForZkLease::class)).asSingleton()
    bind<LeaseManager>().to<ZkLeaseManager>()
  }

  @Provides @ForZkLease
  fun provideTaskQueue(
    clock: Clock,
    @ForZkLease delayQueue: ExplicitReleaseDelayQueue<DelayedTask>
  ): RepeatedTaskQueue {
    return RepeatedTaskQueue.forTesting("zk-lease-poller", clock, delayQueue)
  }

  @Provides @ForZkLease
  fun provideDelayQueue(): ExplicitReleaseDelayQueue<DelayedTask> = ExplicitReleaseDelayQueue()
}
