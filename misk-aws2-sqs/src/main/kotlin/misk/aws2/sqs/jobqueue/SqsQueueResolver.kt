package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import com.google.inject.Singleton
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.jobqueue.QueueName
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SqsQueueResolver @Inject constructor(
  private val sqsClientFactory: SqsClientFactory,
  private val sqsConfig: SqsConfig,
) {
  private val queueUrlCache = ConcurrentHashMap<QueueName, String>()

  /**
   * Get sqs queue URL for a given queue.
   *
   * Results are cached in-memory, call to SQS is effectively blocking.
   */
  fun getQueueUrl(queueName: QueueName): String {
    return queueUrlCache.computeIfAbsent(queueName) {
      val queueConfig = sqsConfig.getQueueConfig(queueName)
      val region = queueConfig.region!!
      val client = sqsClientFactory.get(region)
      val ownerAccountId = queueConfig.account_id
      val retryQueueUrlRequest = GetQueueUrlRequest.builder()
        .queueName(queueName.value)
        .queueOwnerAWSAccountId(ownerAccountId)
        .build()
      val response = client.getQueueUrl(retryQueueUrlRequest).join()
      response.queueUrl()
    }
  }
}
