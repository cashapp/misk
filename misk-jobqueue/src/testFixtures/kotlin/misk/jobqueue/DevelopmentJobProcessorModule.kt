package misk.jobqueue

import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory

class DevelopmentJobProcessorModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<DevelopmentJobProcessor>().dependsOn<ReadyService>())
    install(ServiceModule(keyOf<RepeatedTaskQueue>(ForDevelopmentHandling::class)))
  }

  @Provides
  @ForDevelopmentHandling
  @Singleton
  fun consumerRepeatedTaskQueue(queueFactory: RepeatedTaskQueueFactory): RepeatedTaskQueue {
    return queueFactory.new("development-job-poller")
  }
}
