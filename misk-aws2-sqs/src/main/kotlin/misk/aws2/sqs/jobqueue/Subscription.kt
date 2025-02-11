package misk.aws2.sqs.jobqueue

import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import kotlin.reflect.KClass

/**
 * Subscriptions represents a connection between queue, handler and specific runtime parameters.
 *
 * Channel capacity defines how many pre-read jobs will be waiting for a free handler to pick them up.
 * Parallelism defines how many threads will be dedicated to run the handler.
 * Concurrency defines how many coroutines will run the handler code.
 */
data class Subscription @JvmOverloads constructor(
  val queueName: QueueName,
  val handler: KClass<out JobHandler>,
  val parallelism: Int = 1,
  val concurrency: Int = 1,
  val channelCapacity: Int = 0,
)
