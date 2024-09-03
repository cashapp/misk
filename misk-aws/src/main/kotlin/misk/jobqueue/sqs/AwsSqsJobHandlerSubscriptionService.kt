package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.jobqueue.BatchJobHandler
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val attributeImporter: AwsSqsQueueAttributeImporter,
  private val consumer: SqsJobConsumer,
  private val consumerMapping: ConsumerMapping,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>,
  private val config: AwsSqsJobQueueConfig
) : AbstractIdleService() {
  override fun startUp() {
    check(consumerMapping.individual.isNotEmpty() || consumerMapping.batch.isNotEmpty()) {
      "No handlers have been registered"
    }

    val duplicateQueues = consumerMapping.individual.keys.intersect(consumerMapping.batch.keys)
    check(duplicateQueues.isEmpty()) { "Queues $duplicateQueues have multiple handlers" }

    consumerMapping.individual.forEach { consumer.subscribe(it.key, it.value) }
    consumerMapping.batch.forEach { consumer.subscribe(it.key, it.value) }
    externalQueues.forEach { attributeImporter.import(it.key) }
  }

  override fun shutDown() {
    if (config.safe_shutdown) {
      consumer.unsubscribeAll()
      attributeImporter.shutDown()
      consumer.shutDown()
    }
  }
}

@Singleton
internal class ConsumerMapping @Inject constructor() {
  @com.google.inject.Inject(optional = true)
  var individual: Map<QueueName, JobHandler> = emptyMap()
  @com.google.inject.Inject(optional = true)
  var batch: Map<QueueName, BatchJobHandler> = emptyMap()
}
