package misk.cron

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provides
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.concurrent.ExecutorServiceModule
import misk.inject.AsyncModule
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.inject.toKey
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import wisp.lease.LeaseManager
import java.time.ZoneId

/**
 * Provides cron scheduling functionality for Misk services.
 *
 * @param useMultipleLeases Controls lease coordination strategy. Changing this value may cause
 *   overlapping task execution during deployments.
 *
 *   Example: switching false→true means old pods use cluster-wide leases while new pods use
 *   per-task leases, potentially running the same task on both.
 *
 *   Deploy during downtime or ensure tasks are idempotent.
 */
class CronModule @JvmOverloads constructor(
  private val zoneId: ZoneId,
  private val threadPoolSize: Int = 10,
  private val dependencies: List<Key<out Service>> = listOf(),
  private val installDashboardTab: Boolean = true,
  private val useMultipleLeases: Boolean = false
) : AsyncModule, KInstallOnceModule() {
  override fun configure() {
    install(
      FakeCronModule(
        zoneId = zoneId,
        threadPoolSize = threadPoolSize,
        dependencies = dependencies,
        installDashboardTab = installDashboardTab,
        useMultipleLeases = useMultipleLeases,
      ),
    )
    install(ServiceModule<RepeatedTaskQueue>(ForMiskCron::class).dependsOn<ReadyService>())
    install(
      ServiceModule(
        key = CronTask::class.toKey(),
        dependsOn = dependencies,
      ).dependsOn<ReadyService>(),
    )
  }

  @OptIn(ExperimentalMiskApi::class)
  override fun moduleWhenAsyncDisabled(): KAbstractModule? {
    return FakeCronModule(
      zoneId = zoneId,
      threadPoolSize = threadPoolSize,
      dependencies = dependencies,
      installDashboardTab = installDashboardTab,
      useMultipleLeases = useMultipleLeases,
    )
  }

  @Provides
  @ForMiskCron
  @Singleton
  fun provideTaskQueue(queueFactory: RepeatedTaskQueueFactory): RepeatedTaskQueue =
    queueFactory.new("misk.cron.task-queue")
}

class FakeCronModule @JvmOverloads constructor(
  private val zoneId: ZoneId,
  private val threadPoolSize: Int = 10,
  private val dependencies: List<Key<out Service>> = listOf(),
  private val installDashboardTab: Boolean = false,
  private val useMultipleLeases: Boolean = false,
) : KAbstractModule() {
  override fun configure() {
    bind<ZoneId>().annotatedWith<ForMiskCron>().toInstance(zoneId)
    install(
      ExecutorServiceModule.withFixedThreadPool(
        ForMiskCron::class,
        "misk-cron-cronjob-%d",
        threadPoolSize,
      ),
    )
    install(
      ServiceModule(
        key = CronService::class.toKey(),
        dependsOn = dependencies,
      ).dependsOn<ReadyService>(),
    )

    if (installDashboardTab) {
      // Don't install by default since it adds extra dependencies to downstream tests
      install(CronDashboardTabModule())
    }
  }

  @Provides
  @ForMiskCron
  @Singleton
  fun cronCoordinator(leaseManager: LeaseManager): CronCoordinator =
    if (useMultipleLeases) {
      MultipleLeaseCronCoordinator(leaseManager)
    } else {
      SingleLeaseCronCoordinator(leaseManager)
    }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForMiskCron
