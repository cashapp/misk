package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueUrlRequest
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.jobqueue.QueueName
import misk.logging.getLogger
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

    val queueUrl = try {
      sqs.getQueueUrl(GetQueueUrlRequest().apply {
        queueName = sqsQueueName.value
        queueOwnerAWSAccountId = accountId.value
      }).queueUrl
    } catch (e: QueueDoesNotExistException) {
      log.error(e) { "SQS Queue ${sqsQueueName.value} does not exist" }
      throw e
    }

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

  companion object {
    val log = getLogger<QueueResolver>()
  }
}

internal const val deadLetterQueueSuffix = "_dlq"

val QueueName.isDeadLetterQueue get() = value.endsWith(deadLetterQueueSuffix)
val QueueName.deadLetterQueue
  get() = if (isDeadLetterQueue) this else QueueName(value + deadLetterQueueSuffix)

internal const val retryQueueSuffix = "_retryq"
val QueueName.isRetryQueue get() = value.endsWith(retryQueueSuffix)
val QueueName.retryQueue
  get() = if (isRetryQueue) this else QueueName(value + retryQueueSuffix)

val QueueName.parentQueue
  get() = if (isDeadLetterQueue) {
    QueueName(value.removeSuffix(deadLetterQueueSuffix))
  } else if (isRetryQueue) {
    QueueName(value.removeSuffix(retryQueueSuffix))
  } else {
    this
  }
