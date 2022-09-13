package misk.jobqueue.sqs

import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_PER_QUEUE
import misk.jobqueue.sqs.SqsJobConsumer.Companion.POD_CONSUMERS_PER_QUEUE
import misk.jobqueue.sqs.SqsJobConsumer.Companion.POD_MAX_JOBQUEUE_CONSUMERS
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
  val sqs_sending_request_timeout_ms: Int = 5000,

  val aws_sqs_job_receiver_policy: AwsSqsJobReceiverPolicy = AwsSqsJobReceiverPolicy.ONE_FLAG_ONLY
) : Config


/**
 * AWS SQS consumers are spun by each of a service's pods. Each pod is responsible for running some
 * number on consumers and making sure they are within the parameters of the feature-flags used.
 *
 * Which flags?
 *  * [CONSUMERS_PER_QUEUE]: This flag specifies the total number of consumers across the entire
 *    cluster.
 *  * [POD_CONSUMERS_PER_QUEUE]: This flag specifies the number of consumers a single pod should
 *    have.
 *
 * The [AwsSqsJobReceiverPolicy] gives two options for how consumers are created based on the flags.
 */
enum class AwsSqsJobReceiverPolicy {
  /**
   * This is the original policy. Naming is hard, but this policy will compute receivers as follows.
   * First we choose one flag. If there is a configuration in [POD_CONSUMERS_PER_QUEUE], choose that
   * flag; otherwise choose the [CONSUMERS_PER_QUEUE] flag.
   *
   * If the [POD_CONSUMERS_PER_QUEUE] is chosen, ALL pods will spin up the configured number of
   * consumers. Imagine the flag is configured for 5 consumers, then
   *  5 pods => 25 sqs consumers
   *  10 pods => 50 sqs consumers
   *  100 pods => 500 sqs consumers
   *
   * If the [CONSUMERS_PER_QUEUE] is chosen then we use leases. Consider that the flag is configured
   * with _m_ consumers (globally) so that _m_ leases are available. As the pods come online, they
   * will eagerly spin up consumers until leases run out... they race! Once _m_ leases are handed
   * out any pods that didn't spin up a receiver will not participate in SQS consumption and those
   * that won the race might have up to _m_ receivers.
   */
  ONE_FLAG_ONLY,

  /**
   * This policy uses a combination of these two flags to avoid the worst of both as used in
   * [ONE_FLAG_ONLY] above.
   *
   * The [POD_CONSUMERS_PER_QUEUE] is subject to DOS a service when it scales up. This is especially
   * problematic with auto scaling.
   *
   * The [CONSUMERS_PER_QUEUE] leads to really unbalanced nodes. Throughput suffers and it is really
   * difficult to process high backlogs of messages since usually very few nodes have enough
   * consumers.
   *
   * With this policy, as pods come online they take as many leases as are available OR until they
   * hit the max configured per pod limit. In the interest of not overloading the flag names this
   * max per pod is configured with [POD_MAX_JOBQUEUE_CONSUMERS]
   *
   * This means that the SQS consumers can scale up as the services scales up, but will hit a
   * ceiling specified by the [CONSUMERS_PER_QUEUE] flag. It also means that no pod takes on all the
   * work of processing the SQS jobs.
   */
  BALANCED_MAX,
}
