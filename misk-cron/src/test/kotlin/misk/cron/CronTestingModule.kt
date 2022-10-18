package misk.cron

import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.clustering.weights.FakeClusterWeightModule
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.tasks.DelayedTask
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import java.time.ZoneId

class CronTestingModule : KAbstractModule() {
  override fun configure() {
    val applicationModules: List<KAbstractModule> = listOf(
      FakeLeaseModule(),
      ServiceModule<RepeatedTaskQueue>(ForMiskCron::class),
      FakeClusterWeightModule(),
      MiskTestingServiceModule(),

      // Cron support requires registering the CronJobHandler and the CronRunnerModule.
      FakeCronModule(ZoneId.of("America/Toronto")),
      ServiceModule<CronTask>()
        .dependsOn(keyOf<RepeatedTaskQueue>(ForMiskCron::class)),
    )

    applicationModules.forEach { module -> install(module) }
  }

  @Provides @Singleton
  fun repeatedTaskQueueBackingStorage(): ExplicitReleaseDelayQueue<DelayedTask> {
    return ExplicitReleaseDelayQueue()
  }

  @Provides @Singleton @ForMiskCron
  fun repeatedTaskQueue(
    queueFactory: RepeatedTaskQueueFactory,
    backingStorage: ExplicitReleaseDelayQueue<DelayedTask>
  ): RepeatedTaskQueue {
    return queueFactory.forTesting("my-task-queue", backingStorage)
  }
}
