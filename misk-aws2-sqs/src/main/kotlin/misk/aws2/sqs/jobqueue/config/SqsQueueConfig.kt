package misk.aws2.sqs.jobqueue.config

/**
 * Configuration of a given SQS queue.
 *
 * `parallelism` defines number of threads used for handling the jobs.
 * `concurrency` defines number of coroutines used for handling the jobs.
 * `channel_capacity` defined the buffer size between receiving and handling part.
 * `max_number_of_messages` defines the batch size used to receive messages from the queue.
 * `install_retry_queue` defines if the retry queue should be consumed by default.
 * `wait_timeout` defines how long the client will wait for messages. Defaults to null which
 * will use the queue settings.
 * `visibility_timeout` defines for how long the message will be invisible for subsequent
 * requests. If configured to null, the queue settings will be used.
 * `region` AWS Region of the consumed queue, defaults to the current region.
 * `account_id` AWS Account ID of the consumed queue, defaults to the current account.
 * `queue_name` AWS Queue Name, defaults to the application provided name of the queue.
 */
data class SqsQueueConfig @JvmOverloads constructor(
  val parallelism: Int = 1,
  val concurrency: Int = 1,
  val channel_capacity: Int = 0,
  val max_number_of_messages: Int = 10,
  val install_retry_queue: Boolean = true,
  val wait_timeout: Int? = null,
  val visibility_timeout: Int? = null,
  val region: String? = null,
  val account_id: String? = null,
  val queue_name: String? = null,
)
