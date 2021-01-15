package misk.jobqueue.sqs

/**
 * An [AwsSqsQueueConfig] tells misk about a queue, potentially in another region and/or
 * another account. If the queue is in another account, it will require an IAM policy
 * enabling cross account access
 */
data class AwsSqsQueueConfig(
  val region: String? = null, // defaults to the current region
  val account_id: String? = null, // defaults to the current account
  val sqs_queue_name: String? = null // defaults to the application provided name of the queue
)
