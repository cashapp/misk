package misk.jobqueue.sqs

/**
 * [AwsSqsJobQueueConfig] is the configuration for job queueing backed by Amazon's
 * Simple Queuing Service
 */
class AwsSqsJobQueueConfig(
  /**
   * Number of receiver threads to run per queue. Each receiver thread can pull up to 10 messages
   * at a time, so this parameter controls how many fetches of 10 are outstanding at any one time.
   */
  val concurrent_receivers_per_queue: Int = 1
)