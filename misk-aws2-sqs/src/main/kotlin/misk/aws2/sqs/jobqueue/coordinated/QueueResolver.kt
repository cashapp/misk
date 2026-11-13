package misk.aws2.sqs.jobqueue.coordinated

import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import misk.aws2.sqs.jobqueue.DeadLetterQueueProvider
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.config.AppName
import misk.containers.ContainerUtil
import misk.feature.FeatureFlags
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.moshi.adapter
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException

@Singleton
internal class QueueResolver
@Inject
internal constructor(
  private val currentRegion: AwsRegion,
  private val currentAccount: AwsAccountId,
  @AppName private val appName: String,
  private val featureFlags: FeatureFlags,
  private val sqsClientFactory: SqsClientFactory,
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
    return resolve(dlqProvider.deadLetterQueueFor(q), false)
  }

  private fun resolve(q: QueueName, forSqsReceiving: Boolean): ResolvedQueue {
    val queueConfig = externalQueues[q] ?: AwsSqsQueueConfig()
    val sqsQueueName = queueConfig.sqs_queue_name?.let { QueueName(it) } ?: q
    val region = queueConfig.region?.let { AwsRegion(it) } ?: currentRegion
    val accountId = queueConfig.account_id?.let { AwsAccountId(it) } ?: currentAccount
    val sqs = if (forSqsReceiving) sqsClientFactory.getForReceiving(region) else sqsClientFactory.getForSending(region)

    val queueUrl =
      try {
        val queueUrl =
          sqs
            .getQueueUrl(
              GetQueueUrlRequest.builder()
                .queueName(sqsQueueName.value)
                .queueOwnerAWSAccountId(accountId.value)
                .build()
            )
            .queueUrl()
        ensureUrlWithProperTarget(queueUrl)
      } catch (e: QueueDoesNotExistException) {
        log.error(e) { "SQS Queue ${sqsQueueName.value} does not exist" }
        throw e
      }

    val queueMaxRetries = extractMaxReceiveCount(sqs, queueUrl)
    return ResolvedQueue(
      q,
      sqsQueueName,
      queueUrl,
      region,
      accountId,
      sqs,
      sqsClientFactory.getBatchManager(region),
      appName,
      featureFlags,
      queueMaxRetries,
    )
  }

  data class RedrivePolicy(val maxReceiveCount: Int, val deadLetterTargetArn: String)

  private fun extractMaxReceiveCount(sqs: software.amazon.awssdk.services.sqs.SqsClient, queueUrl: String): Int {
    return try {
      val redrivePolicyJson =
        sqs
          .getQueueAttributes(
            GetQueueAttributesRequest.builder()
              .queueUrl(queueUrl)
              .attributeNames(QueueAttributeName.REDRIVE_POLICY)
              .build()
          )
          .attributes()[QueueAttributeName.REDRIVE_POLICY]

      if (redrivePolicyJson.isNullOrEmpty()) {
        return 10
      }

      redrivePolicyAdapter.fromJson(redrivePolicyJson)?.maxReceiveCount ?: 10
    } catch (e: Exception) {
      10
    }
  }

  private fun ensureUrlWithProperTarget(url: String): String {
    return if (ContainerUtil.isRunningInDocker) {
      url.replace("localhost", hostInternalTarget).replace("127.0.0.1", hostInternalTarget)
    } else {
      url
    }
  }

  companion object {
    val log = getLogger<QueueResolver>()
  }
}
