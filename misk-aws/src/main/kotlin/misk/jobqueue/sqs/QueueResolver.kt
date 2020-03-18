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
  @ForSqsReceiving private val defaultForReceivingSQS: AmazonSQS,
  @ForSqsReceiving private val crossRegionForReceivingSQS: Map<AwsRegion, AmazonSQS>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>
) {
  private val forSendingMapping = ConcurrentHashMap<QueueName, ResolvedQueue>()
  private val forReceivingMapping = ConcurrentHashMap<QueueName, ResolvedQueue>()

  fun getForSending(q: QueueName): ResolvedQueue {
    return forSendingMapping.computeIfAbsent(q) { resolve(it, false) }
  }

  fun getForReceiving(q: QueueName): ResolvedQueue {
    return forReceivingMapping.computeIfAbsent(q) { resolve(it, true) }
  }

  private fun resolve(q: QueueName, forSqsReceiving: Boolean): ResolvedQueue {
    if (q.isDeadLetterQueue) {
      return resolveDeadLetterQueue(q, forSqsReceiving)
    }

    val queueConfig = externalQueues[q] ?: AwsSqsQueueConfig()
    val sqsQueueName = queueConfig.sqs_queue_name?.let { QueueName(it) } ?: q
    val region = queueConfig.region?.let { AwsRegion(it) } ?: currentRegion
    val accountId = queueConfig.account_id?.let { AwsAccountId(it) } ?: currentAccount

    val sqs = if (region == currentRegion) {
      if (forSqsReceiving) defaultForReceivingSQS else defaultSQS
    } else {
      if (forSqsReceiving) crossRegionForReceivingSQS[region] else crossRegionSQS[region]
    }

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

  private fun resolveDeadLetterQueue(q: QueueName, forSqsReceiving: Boolean): ResolvedQueue {
    val parentQueue = resolve(q.parentQueue, forSqsReceiving)
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
  get() = when {
    isDeadLetterQueue -> QueueName(value.removeSuffix(deadLetterQueueSuffix))
    isRetryQueue -> QueueName(value.removeSuffix(retryQueueSuffix))
    else -> this
  }
