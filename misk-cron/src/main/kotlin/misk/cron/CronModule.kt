package misk.cron

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.concurrent.ExecutorServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import java.time.ZoneId
import javax.inject.Qualifier

class CronModule @JvmOverloads constructor(
  private val zoneId: ZoneId,
  private val threadPoolSize: Int = 10,
  private val dependencies: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    install(FakeCronModule(zoneId, threadPoolSize, dependencies))
    install(ServiceModule<RepeatedTaskQueue>(ForMiskCron::class).dependsOn<ReadyService>())
    install(
      ServiceModule(
        key = CronTask::class.toKey(),
        dependsOn = dependencies,
      ).dependsOn<ReadyService>()
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
  private val dependencies: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    bind<ZoneId>().annotatedWith<ForMiskCron>().toInstance(zoneId)
    install(
      ExecutorServiceModule.withFixedThreadPool(
        ForMiskCron::class,
        "misk-cron-cronjob-%d",
        threadPoolSize
      )
    )
    install(
      ServiceModule(
        key = CronService::class.toKey(),
        dependsOn = dependencies
      ).dependsOn<ReadyService>()
    )
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForMiskCron
