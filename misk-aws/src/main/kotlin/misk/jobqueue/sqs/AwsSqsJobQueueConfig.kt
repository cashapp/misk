package misk.jobqueue.sqs

import misk.config.Config
import misk.tasks.RepeatedTaskQueueConfig

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
  val external_queues: Map<String, AwsSqsQueueConfig> = mapOf(),

  /**
   * Number of jobs that can be processed concurrently.
   */
  val consumer_thread_pool_size: Int = 4,

  /**
   * Max number of messages to pull from SQS with each request.
   */
  val message_batch_size: Int = 10,

  /**
   * Task queue configuration, which should have a `num_parallel_tasks` equal or greater than the
   * number of consumed queues. If undefined, an unbounded number of parallel tasks will be used.
   */
  val task_queue: RepeatedTaskQueueConfig? = null
) : Config
