package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import wisp.logging.getLogger

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
        config.per_queue_overrides[queueName.value] ?: config.all_queues,
      )
    }
  }

  override fun shutDown() {
  }

  companion object {
    private val logger = getLogger<SubscriptionService>()
  }
}
