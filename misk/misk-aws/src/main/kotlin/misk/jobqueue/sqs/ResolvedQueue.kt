package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import misk.cloud.aws.AwsAccountId
import misk.jobqueue.QueueName

/** [ResolvedQueue] provides information needed to reach an SQS queue */
internal class ResolvedQueue(
  val name: QueueName,
  val sqsQueueName: QueueName,
  val url: String,
  val accountId: AwsAccountId,
  val client: AmazonSQS
)