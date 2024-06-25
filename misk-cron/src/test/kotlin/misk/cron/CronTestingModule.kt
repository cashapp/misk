package misk.cron

import com.google.inject.Provides
import jakarta.inject.Singleton
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

class CronTestingModule @JvmOverloads constructor(private val cronLeaseBehavior: CronLeaseBehavior) : KAbstractModule() {
  override fun configure() {
    val applicationModules: List<KAbstractModule> = listOf(
      FakeLeaseModule(),
      ServiceModule<RepeatedTaskQueue>(ForMiskCron::class),
      FakeClusterWeightModule(),
      MiskTestingServiceModule(),

      // Cron support requires registering the CronJobHandler and the CronRunnerModule.
      FakeCronModule(
        zoneId = ZoneId.of("America/Toronto"),
        cronLeaseBehavior = cronLeaseBehavior,
        ),
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
