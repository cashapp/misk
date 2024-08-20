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
import misk.web.metadata.MetadataModule
import java.time.ZoneId

class CronModule @JvmOverloads constructor(
  private val zoneId: ZoneId,
  private val threadPoolSize: Int = 10,
  private val dependencies: List<Key<out Service>> = listOf(),
  private val cronLeaseBehavior: CronLeaseBehavior = CronLeaseBehavior.ONE_LEASE_PER_CRON,
) : KInstallOnceModule() {
  override fun configure() {
    install(FakeCronModule(zoneId, threadPoolSize, dependencies))
    install(ServiceModule<RepeatedTaskQueue>(ForMiskCron::class).dependsOn<ReadyService>())
    install(
      ServiceModule(
        key = CronTask::class.toKey(),
        dependsOn = dependencies,
      ).dependsOn<ReadyService>()
    )
    bind<CronLeaseBehavior>().annotatedWith<ForMiskCron>().toInstance(cronLeaseBehavior)
    install(MetadataModule(CronMetadataProvider()))
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
  private val cronLeaseBehavior: CronLeaseBehavior = CronLeaseBehavior.ONE_LEASE_PER_CLUSTER,
  ) : KAbstractModule() {
  override fun configure() {
    bind<ZoneId>().annotatedWith<ForMiskCron>().toInstance(zoneId)
    bind<CronLeaseBehavior>().annotatedWith<ForMiskCron>().toInstance(cronLeaseBehavior)
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
    install(MetadataModule(CronMetadataProvider()))
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForMiskCron

enum class CronLeaseBehavior {
  ONE_LEASE_PER_CLUSTER,
  ONE_LEASE_PER_CRON,
}
