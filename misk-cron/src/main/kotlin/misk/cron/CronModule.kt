package misk.cron

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provides
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.concurrent.ExecutorServiceModule
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.inject.toKey
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import java.time.ZoneId

class CronModule @JvmOverloads constructor(
  private val zoneId: ZoneId,
  private val threadPoolSize: Int = 10,
  private val dependencies: List<Key<out Service>> = listOf(),
  private val installDashboardTab: Boolean = true,
) : KInstallOnceModule() {
  override fun configure() {
    install(
      FakeCronModule(
        zoneId = zoneId,
        threadPoolSize = threadPoolSize,
        dependencies = dependencies,
        installDashboardTab = installDashboardTab,
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
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForMiskCron
