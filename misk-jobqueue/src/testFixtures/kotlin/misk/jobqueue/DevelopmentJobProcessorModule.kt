package misk.jobqueue

import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.inject.toKey
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory

class DevelopmentJobProcessorModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<DevelopmentJobProcessor>())
    install(ServiceModule(keyOf<RepeatedTaskQueue>(ForDevelopmentHandling::class)))
  }

  @Provides @ForDevelopmentHandling @Singleton
  fun consumerRepeatedTaskQueue(queueFactory: RepeatedTaskQueueFactory): RepeatedTaskQueue {
    return queueFactory.new("development-job-poller")
  }
}
