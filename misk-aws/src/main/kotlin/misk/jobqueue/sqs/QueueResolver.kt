package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueUrlRequest
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.jobqueue.QueueName
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class QueueResolver @Inject internal constructor(
  private val currentRegion: AwsRegion,
  private val currentAccount: AwsAccountId,
  private val defaultSQS: AmazonSQS,
  private val crossRegionSQS: Map<AwsRegion, AmazonSQS>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>
) {
  private val mapping = ConcurrentHashMap<QueueName, ResolvedQueue>()

  operator fun get(q: QueueName): ResolvedQueue {
    return mapping.computeIfAbsent(q) { resolve(it) }
  }

  private fun resolve(q: QueueName): ResolvedQueue {
    if (q.isDeadLetterQueue) {
      return resolveDeadLetterQueue(q)
    }

    val queueConfig = externalQueues[q] ?: AwsSqsQueueConfig()
    val sqsQueueName = queueConfig.sqs_queue_name?.let { QueueName(it) } ?: q
    val region = queueConfig.region?.let { AwsRegion(it) } ?: currentRegion
    val accountId = queueConfig.account_id?.let { AwsAccountId(it) } ?: currentAccount

    val sqs = if (region == currentRegion) defaultSQS else crossRegionSQS[region]

    checkNotNull(sqs) { "could not find SQS client for ${region.name}" }

    val queueUrl = sqs.getQueueUrl(GetQueueUrlRequest().apply {
      queueName = sqsQueueName.value
      queueOwnerAWSAccountId = accountId.value
    }).queueUrl

    return ResolvedQueue(q, sqsQueueName, queueUrl, region, accountId, sqs)
  }

  private fun resolveDeadLetterQueue(q: QueueName): ResolvedQueue {
    val parentQueue = resolve(q.parentQueue)
    val sqsQueueName = QueueName(parentQueue.sqsQueueName.value + deadLetterQueueSuffix)
    val queueUrl = parentQueue.call { client ->
      client.getQueueUrl(GetQueueUrlRequest().apply {
        queueName = sqsQueueName.value
        queueOwnerAWSAccountId = parentQueue.accountId.value
      })
    }.queueUrl

    return ResolvedQueue(
        q, sqsQueueName, queueUrl, parentQueue.region, parentQueue.accountId, parentQueue.client)
  }
}

internal const val deadLetterQueueSuffix = "_dlq"

val QueueName.isDeadLetterQueue get() = value.endsWith(deadLetterQueueSuffix)
val QueueName.deadLetterQueue
  get() = if (isDeadLetterQueue) this else QueueName(value + deadLetterQueueSuffix)

val QueueName.parentQueue
  get() = if (isDeadLetterQueue) QueueName(value.removeSuffix(deadLetterQueueSuffix)) else this
