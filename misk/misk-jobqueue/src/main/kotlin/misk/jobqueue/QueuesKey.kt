package misk.jobqueue

/**
 * Represents an ordered list of queue names, for building a priority queue using underlying queues.
 */
data class QueuesKey(
  val names: List<QueueName>
)
