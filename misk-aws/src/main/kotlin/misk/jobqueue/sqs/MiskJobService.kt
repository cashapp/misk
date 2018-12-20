package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class MiskJobService constructor(
  private var jobConsumer: JobConsumer,
  private var jobQueues: Map<QueueName, Provider<JobHandler>>
) : AbstractIdleService() {
  override fun startUp() {
    for ((queueName, handler) in jobQueues) {
      jobConsumer.subscribe(queueName, handler.get())
    }
  }

  override fun shutDown() {
    // Nothing to do.
  }
}
