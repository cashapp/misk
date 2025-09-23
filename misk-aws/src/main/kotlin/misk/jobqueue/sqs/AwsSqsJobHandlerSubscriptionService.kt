package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.environment.EnvVarLoader
import misk.jobqueue.BatchJobHandler
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.logging.getLogger

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val attributeImporter: AwsSqsQueueAttributeImporter,
  private val consumer: SqsJobConsumer,
  private val individualConsumerMapping: Map<QueueName, JobHandler>,
  private val batchConsumerMapping: Map<QueueName, BatchJobHandler>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>,
  private val config: AwsSqsJobQueueConfig,
  private val envVarLoader: EnvVarLoader
) : AbstractIdleService() {
  override fun startUp() {
    check(individualConsumerMapping.isNotEmpty() || batchConsumerMapping.isNotEmpty()) {
      "No handlers have been registered"
    }

    val duplicateQueues = individualConsumerMapping.keys.intersect(batchConsumerMapping.keys)
    check(duplicateQueues.isEmpty()) { "Queues $duplicateQueues have multiple handlers" }

    if (!asyncTasksDisabled()) {
      individualConsumerMapping.forEach { consumer.subscribe(it.key, it.value) }
      batchConsumerMapping.forEach { consumer.subscribe(it.key, it.value) }
    } else {
      val allQueues = (individualConsumerMapping.keys + batchConsumerMapping.keys).joinToString(", ")
      logger.info { "Async tasks are disabled with DISABLE_ASYNC_TASKS, not subscribing to queues ($allQueues)" }
    }

    externalQueues.forEach { attributeImporter.import(it.key) }
  }

  override fun shutDown() {
    if (config.safe_shutdown) {
      consumer.unsubscribeAll()
      attributeImporter.shutDown()
      consumer.shutDown()
    }
  }

  private fun asyncTasksDisabled(): Boolean {
    val envVarValue = envVarLoader.getEnvironmentVariableOrDefault("DISABLE_ASYNC_TASKS", "").trim()
    return envVarValue.toBoolean() || envVarValue == "1"
  }

  companion object {
    private val logger = getLogger<AwsSqsJobHandlerSubscriptionService>()
  }
}
