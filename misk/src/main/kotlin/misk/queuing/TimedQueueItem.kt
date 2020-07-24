package misk.queuing

import java.util.Objects

/**
 * A wrapper class around any queued type which provides a startTime field for computing
 * the time it spent in the queue
 */
internal data class TimedQueueItem<T>(
  val value: T,
  val startTime: Long
) {

  override fun equals(other: Any?): Boolean {
    val otherItem = other as? TimedQueueItem<*> ?: return false
    // equals and hashCode should not use startTime
    // startTime should only be used for computing delay
    return value == otherItem.value
  }

  override fun hashCode(): Int {
    return Objects.hash(value)
  }
}
