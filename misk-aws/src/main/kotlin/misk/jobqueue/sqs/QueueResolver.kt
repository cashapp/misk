package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueUrlRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.containers.ContainerUtil
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.moshi.adapter

@Singleton
internal class QueueResolver
@Inject
internal constructor(
  private val currentRegion: AwsRegion,
  private val currentAccount: AwsAccountId,
  private val defaultSQS: AmazonSQS,
  private val crossRegionSQS: Map<AwsRegion, AmazonSQS>,
  @ForSqsReceiving private val defaultForReceivingSQS: AmazonSQS,
  @ForSqsReceiving private val crossRegionForReceivingSQS: Map<AwsRegion, AmazonSQS>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>,
  private val dlqProvider: DeadLetterQueueProvider,
  private val moshi: Moshi,
) {
  private val forSendingMapping = ConcurrentHashMap<QueueName, ResolvedQueue>()
  private val forReceivingMapping = ConcurrentHashMap<QueueName, ResolvedQueue>()
  private val hostInternalTarget = "host.docker.internal"
  private val redrivePolicyAdapter = moshi.adapter(RedrivePolicy::class.java)

  fun getForSending(q: QueueName): ResolvedQueue {
    return forSendingMapping.computeIfAbsent(q) { resolve(it, false) }
  }

  fun getForReceiving(q: QueueName): ResolvedQueue {
    return forReceivingMapping.computeIfAbsent(q) { resolve(it, true) }
  }

  fun getDeadLetter(q: QueueName): ResolvedQueue {
    // Dead-letter queue is always used for sending, never for receiving.
    return resolve(dlqProvider.deadLetterQueueFor(q), false)
  }

  private fun resolve(q: QueueName, forSqsReceiving: Boolean): ResolvedQueue {
    val queueConfig = externalQueues[q] ?: AwsSqsQueueConfig()
    val sqsQueueName = queueConfig.sqs_queue_name?.let { QueueName(it) } ?: q
    val region = queueConfig.region?.let { AwsRegion(it) } ?: currentRegion
    val accountId = queueConfig.account_id?.let { AwsAccountId(it) } ?: currentAccount

    val sqs =
      if (region == currentRegion) {
        if (forSqsReceiving) defaultForReceivingSQS else defaultSQS
      } else {
        if (forSqsReceiving) crossRegionForReceivingSQS[region] else crossRegionSQS[region]
      }

    checkNotNull(sqs) { "could not find SQS client for ${region.name}" }
    val queueUrl =
      try {
        val queue_url =
          sqs
            .getQueueUrl(
              GetQueueUrlRequest().apply {
                queueName = sqsQueueName.value
                queueOwnerAWSAccountId = accountId.value
              }
            )
            .queueUrl
        ensureUrlWithProperTarget(queue_url)
      } catch (e: QueueDoesNotExistException) {
        log.error(e) { "SQS Queue ${sqsQueueName.value} does not exist" }
        throw e
      }

    val queueMaxRetries = extractMaxReceiveCount(sqs, queueUrl)
    return ResolvedQueue(q, sqsQueueName, queueUrl, region, accountId, sqs, queueMaxRetries)
  }

  data class RedrivePolicy(val maxReceiveCount: Int, val deadLetterTargetArn: String)

  private fun extractMaxReceiveCount(sqs: AmazonSQS, queueUrl: String): Int {
    return try {
      val redrivePolicyJson =
        sqs
          .getQueueAttributes(
            GetQueueAttributesRequest().withQueueUrl(queueUrl).withAttributeNames(QueueAttributeName.RedrivePolicy)
          )
          .attributes["RedrivePolicy"]

      if (redrivePolicyJson.isNullOrEmpty()) {
        return 10
      }

      redrivePolicyAdapter.fromJson(redrivePolicyJson)?.maxReceiveCount ?: 10
    } catch (e: Exception) {
      10
    }
  }

  private fun ensureUrlWithProperTarget(url: String): String {
    if (ContainerUtil.isRunningInDocker)
      return url.replace("localhost", hostInternalTarget).replace("127.0.0.1", hostInternalTarget)
    else return url
  }

  companion object {
    val log = getLogger<QueueResolver>()
  }
}

internal const val deadLetterQueueSuffix = "_dlq"

internal val QueueName.isDeadLetterQueue
  get() = value.endsWith(deadLetterQueueSuffix)
internal val QueueName.deadLetterQueue
  get() = if (isDeadLetterQueue) this else QueueName(parentQueue.value + deadLetterQueueSuffix)

internal const val retryQueueSuffix = "_retryq"
val QueueName.isRetryQueue
  get() = value.endsWith(retryQueueSuffix)
val QueueName.retryQueue
  get() = if (isRetryQueue) this else QueueName(parentQueue.value + retryQueueSuffix)

val QueueName.parentQueue
  get() =
    when {
      isDeadLetterQueue -> QueueName(value.removeSuffix(deadLetterQueueSuffix))
      isRetryQueue -> QueueName(value.removeSuffix(retryQueueSuffix))
      else -> this
    }
