package misk.aws2.sqs.jobqueue.config

/**
 * Configuration of a given SQS queue.
 *
 * `parallelism` defines number of threads used for handling the jobs. `concurrency` defines number of coroutines used
 * for handling the jobs. `channel_capacity` defined the buffer size between receiving and handling part.
 * `max_number_of_messages` defines the batch size used to receive messages from the queue. `install_retry_queue`
 * defines if the retry queue should be consumed by default. `wait_timeout` defines how long the client will wait for
 * messages. Defaults to null which will use the queue settings. `visibility_timeout` defines for how long the message
 * will be invisible for subsequent requests. If configured to null, the queue settings will be used. `region` AWS
 * Region of the consumed queue, defaults to the current region. `account_id` AWS Account ID of the consumed queue,
 * defaults to the current account. `queue_name` AWS Queue Name, defaults to the application provided name of the queue.
 * `shutdown_grace_period_ms` defines how long shutdown waits for an in-flight receive to complete before aborting it.
 * Set it to 0 to abort in-flight receives immediately. Defaults to null, which uses
 * [SqsQueueConfig.DEFAULT_SHUTDOWN_GRACE_PERIOD_MS].
 */
data class SqsQueueConfig
@JvmOverloads
constructor(
  val parallelism: Int = 1,
  val concurrency: Int = 1,
  val channel_capacity: Int = 0,
  val max_number_of_messages: Int = 10,
  val install_retry_queue: Boolean = true,
  val wait_timeout: Int? = null,
  val visibility_timeout: Int? = null,
  val region: String? = null,
  val account_id: String? = null,
  val shutdown_grace_period_ms: Long? = null,
) {
  companion object {
    /**
     * How long shutdown waits for an in-flight receive to complete before aborting it.
     *
     * A long poll that has messages returns immediately, so this only needs to cover a receive that is about to
     * deliver. One that is still waiting out its `wait_timeout` has nothing to lose by being canceled.
     */
    const val DEFAULT_SHUTDOWN_GRACE_PERIOD_MS = 1000L
  }
}
