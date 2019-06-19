package misk.clustering.zookeeper

import com.google.inject.Provides
import com.google.inject.Singleton
import misk.clustering.fake.FakeClusterModule
import misk.clustering.weights.FakeClusterWeightModule
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.tasks.DelayedTask
import misk.tasks.RepeatedTaskQueue
import misk.zookeeper.testing.ZkTestModule
import java.time.Clock

internal class ZkLeaseTestModule : KAbstractModule() {
  override fun configure() {
    bind<String>().annotatedWith<AppName>().toInstance("my-app")
    install(FakeClusterModule())
    install(ZkTestModule(ForZkLease::class))
    install(ZkLeaseCommonModule())
    install(FakeClusterWeightModule())
  }

  @Provides @ForZkLease @Singleton
  fun provideTaskQueue(
    clock: Clock,
    @ForZkLease delayQueue: ExplicitReleaseDelayQueue<DelayedTask>
  ): RepeatedTaskQueue {
    return RepeatedTaskQueue.forTesting("zk-lease-poller", clock, delayQueue)
  }

  @Provides @ForZkLease
  fun provideDelayQueue(): ExplicitReleaseDelayQueue<DelayedTask> = ExplicitReleaseDelayQueue()
}
