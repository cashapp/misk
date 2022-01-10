package misk.jobqueue.sqs

import misk.tasks.RepeatedTaskQueueConfig
import wisp.config.Config

/**
 * [AwsSqsJobQueueConfig] is the configuration for job queueing backed by Amazon's
 * Simple Queuing Service
 */
class AwsSqsJobQueueConfig(
  /**
   * External queues is a set of externally owned SQS queues accessed by this service, mapping
   * an internal queue name to the (account ID, region, name) of the queue in the external account
   */
  val external_queues: Map<String, AwsSqsQueueConfig> = mapOf(),

  /**
   * Max number of messages to pull from SQS with each request.
   */
  @Deprecated("Since this flag is set for all queues, this has been replaced with a Launch " +
    "Darkly flag called jobqueue-consumers-fetch-batch-size. The value of this flag is set as 10 by default " +
    "and can be customised per queue.")
  val message_batch_size: Int = 10,

  /**
   * Task queue configuration, which should have a `num_parallel_tasks` equal or greater than the
   * number of consumed queues. If undefined, an unbounded number of parallel tasks will be used.
   */
  val task_queue: RepeatedTaskQueueConfig? = null,

  /**
   * Frequency used to import Queue Attributes in milliseconds.
   */
  val queue_attribute_importer_frequency_ms: Long = 1000,

  /**
   * Socket timeout to reach SQS with for *sending*, not including retries.
   * We only apply this for sending because receiving uses long-polling,
   * which explicitly leverages a longer request time.
   * We use the default retry strategy with SQS, which retries 3 times.
   * As a result, your app could potentially spend 3 x this timeout talking to SQS.
   */
  val sqs_sending_socket_timeout_ms: Int = 5000,

  /** Connect timeout to reach SQS with for *sending*. */
  val sqs_sending_connect_timeout_ms: Int = 1000,

  /**
   * Request timeout to reach SQS with for *sending*, not including retries.
   * We only apply this for sending because receiving uses long-polling,
   * which explicitly leverages a longer request time.
   * We use the default retry strategy with SQS, which retries 3 times.
   * As a result, your app could potentially spend 3 x this timeout talking to SQS.
   */
  val sqs_sending_request_timeout_ms: Int = 5000
) : Config
