package misk.jobqueue.sqs

import misk.config.Config

/**
 * [AwsSqsJobQueueConfig] is the configuration for job queueing backed by Amazon's
 * Simple Queuing Service
 */
class AwsSqsJobQueueConfig(
  /**
   * Number of receiver threads to run per queue. Each receiver thread can pull up to 10 messages
   * at a time, so this parameter controls how many fetches of 10 are outstanding at any one time.
   */
  val concurrent_receivers_per_queue: Int = 1,

  /**
   * External queues is a set of externally owned SQS queues accessed by this service, mapping
   * an internal queue name to the (account ID, region, name) of the queue in the external account
   */
  val external_queues: Map<String, AwsSqsQueueConfig> = mapOf()
) : Config

