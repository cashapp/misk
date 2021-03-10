package misk.cron

import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import java.time.ZoneId
import javax.inject.Qualifier

class CronModule(
  private val zoneId: ZoneId,
  private val queueName: QueueName
) : KAbstractModule() {

  override fun configure() {
    bind<ZoneId>().annotatedWith<ForMiskCron>().toInstance(zoneId)
    bind<QueueName>().annotatedWith<ForMiskCron>().toInstance(queueName)
    install(ServiceModule<RepeatedTaskQueue>(ForMiskCron::class))
    install(ServiceModule<CronTask>())
    install(ServiceModule<CronService>())
  }

  @Provides
  @ForMiskCron
  @Singleton
  fun provideTaskQueue(queueFactory: RepeatedTaskQueueFactory): RepeatedTaskQueue =
    queueFactory.new("misk.cron.task-queue")
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ForMiskCron
