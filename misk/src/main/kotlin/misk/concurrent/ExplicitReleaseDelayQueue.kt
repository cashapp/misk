package misk.concurrent

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Delayed
import java.util.concurrent.PriorityBlockingQueue

/**
 * An [ExplicitReleaseDelayQueue] is an [ExplicitReleaseBlockingQueue] that release elements
 * in the order in which they expire
 */
class ExplicitReleaseDelayQueue<T : Delayed> private constructor(
  private val delegate: ExplicitReleaseBlockingQueue<T>
) : BlockingQueue<T> by delegate {
  constructor() : this(
      ExplicitReleaseBlockingQueue(PriorityBlockingQueue<T>(), PriorityBlockingQueue<T>())
  )

  fun release(n: Int) = delegate.release(n)
  fun releaseAll() = delegate.releaseAll()
  fun peekPending(): T? = delegate.peekPending()
}
