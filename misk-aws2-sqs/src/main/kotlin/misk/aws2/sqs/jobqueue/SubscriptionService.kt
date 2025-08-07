package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import misk.logging.getLogger

@Singleton
class SubscriptionService @Inject constructor(
  private val consumer: SqsJobConsumer,
  private val handlers: Map<QueueName, JobHandler>,
  private val config: SqsConfig,
): AbstractIdleService() {
  override fun startUp() {
    logger.info {
      "Starting AWS SQS SubscriptionService with config=$config"
    }
    handlers.forEach { (queueName, handler) ->
      consumer.subscribe(
        queueName,
        handler,
        config.getQueueConfig(queueName),
      )
    }
  }

  override fun shutDown() {
  }

  companion object {
    private val logger = getLogger<SubscriptionService>()
  }
}
