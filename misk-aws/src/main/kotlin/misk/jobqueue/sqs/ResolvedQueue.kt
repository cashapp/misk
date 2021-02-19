package misk.jobqueue.sqs

import com.amazonaws.AmazonClientException
import com.amazonaws.services.sqs.AmazonSQS
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.jobqueue.QueueName

/** [ResolvedQueue] provides information needed to reach an SQS queue */
internal class ResolvedQueue(
  val name: QueueName,
  val sqsQueueName: QueueName,
  val url: String,
  val region: AwsRegion,
  val accountId: AwsAccountId,
  val client: AmazonSQS
) {

  val queueName: String
    get() = name.value

  /**
   * Invokes the lambda with this queue's [AmazonSQS] client. Exceptions thrown by the client
   * are wrapped in a [SQSException].
   */
  fun <T> call(lambda: (AmazonSQS) -> T): T {
    try {
      return lambda.invoke(client)
    } catch (e: AmazonClientException) {
      throw SQSException(e, this)
    }
  }

  /** Wraps AWS client errors, adding queue metadata to the exception message */
  class SQSException(
    cause: AmazonClientException,
    queue: ResolvedQueue
  ) : RuntimeException(
    "${cause.message} (sqsQueue=${queue.sqsQueueName} region=${queue.region})",
    cause
  )
}
