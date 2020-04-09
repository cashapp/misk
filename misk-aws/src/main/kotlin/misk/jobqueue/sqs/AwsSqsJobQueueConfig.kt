package misk.jobqueue.sqs

import misk.config.Config
import misk.tasks.RepeatedTaskQueueConfig

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
  val message_batch_size: Int = 10,

  /**
   * Task queue configuration, which should have a `num_parallel_tasks` equal or greater than the
   * number of consumed queues. If undefined, an unbounded number of parallel tasks will be used.
   */
  val task_queue: RepeatedTaskQueueConfig? = null,

  /**
   * The number of receivers will be distributed among the app cluster If [clustered_consumers] is
   * true. Otherwise, each app in the cluster will run the number of receivers.
   *
   * The number of receivers is configured by [Feature("jobqueue-consumers")].
   */
  val clustered_consumers: Boolean = true,

  /**
   * Frequency used to import Queue Attributes in milliseconds.
   */
  val queue_attribute_importer_frequency_ms: Long = 1000,

  /**
   * Name of the global dead-letter queue. Optional; when set, explicitly dead-lettered jobs are
   * sent to the specified queue, regardless of which queue they were originally submitted to/read
   * from. When undefined, explicitly dead-lettered jobs are sent to a dead-letter queue named after
   * the parent queue, with a "_dlq" suffix (e.g. "barb-queue" -> "barb-queue_dlq").
   *
   * All jobs contain a `_jobqueue-metadata` custom attribute. Any jobs sent to the global
   * dead-letter queue can be traced to their original main queue by inspecting this property.
   *
   * Make sure this property is set or unset according to the AWS redrive policy for queues used
   * by your app.
   */
  val global_dead_letter_queue_name: String? = null
) : Config
