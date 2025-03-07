package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import com.google.inject.Singleton
import misk.jobqueue.QueueName
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import java.util.concurrent.ConcurrentHashMap

@Singleton
class QueueResolver @Inject constructor(
  private val client: SqsAsyncClient,
) {
  private val queueUrlCache = ConcurrentHashMap<QueueName, String>()

  /**
   * Get sqs queue URL for a given queue.
   *
   * Results are cached in-memory, call to SQS is effectively blocking.
   */
  fun getQueueUrl(queueName: QueueName): String {
    return queueUrlCache.computeIfAbsent(queueName) {
      val retryQueueUrlRequest = GetQueueUrlRequest.builder()
        .queueName(queueName.value)
        .build()
      val response = client.getQueueUrl(retryQueueUrlRequest).join()
      response.queueUrl()
    }
  }
}
