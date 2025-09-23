package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.environment.EnvVarLoader
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import misk.logging.getLogger

@Singleton
class SubscriptionService @Inject constructor(
  private val consumer: SqsJobConsumer,
  private val handlers: Map<QueueName, JobHandler>,
  private val config: SqsConfig,
  private val envVarLoader: EnvVarLoader
) : AbstractIdleService() {
  override fun startUp() {
    logger.info {
      "Starting AWS SQS SubscriptionService with config=$config"
    }
    if (!asyncTasksDisabled()) {
      handlers.forEach { (queueName, handler) ->
        consumer.subscribe(
          queueName,
          handler,
          config.getQueueConfig(queueName),
        )
      }
    } else {
      val allQueues = handlers.keys.joinToString(", ")
      logger.info { "Async tasks are disabled with DISABLE_ASYNC_TASKS, not subscribing to queues ($allQueues)" }
    }
  }

  override fun shutDown() {
  }

  private fun asyncTasksDisabled(): Boolean {
    val envVarValue = envVarLoader.getEnvironmentVariableOrDefault("DISABLE_ASYNC_TASKS", "").trim()
    return envVarValue.toBoolean() || envVarValue == "1"
  }

  companion object {
    private val logger = getLogger<SubscriptionService>()
  }
}
