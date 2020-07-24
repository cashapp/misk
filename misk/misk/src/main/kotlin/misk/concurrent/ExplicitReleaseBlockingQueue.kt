package misk.concurrent

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * An [ExplicitReleaseBlockingQueue] is a [BlockingQueue] that only returns elements from
 * [BlockingQueue.take], [BlockingQueue.poll], and [BlockingQueue.peek] after a call to
 * [ExplicitReleaseBlockingQueue.release]. Used by tests that want to explicitly control when
 * pollers receive queued items.
 */
class ExplicitReleaseBlockingQueue<T> internal constructor(
  private val visible: BlockingQueue<T>,
  private val pending: BlockingQueue<T>
) : BlockingQueue<T> {
  constructor() : this(LinkedBlockingQueue<T>(), LinkedBlockingQueue<T>())

  /**
   * releases up to n items from the pending queue, making them visible to [take], [poll], [peek],
   * etc. This method does not "extend credit"; if n > number of pending items on the queue, every
   * currently pending item is made visible, but any items added in the future continue to go
   * onto the pending queue
   *
   * @return the number of items actually releases
   */
  fun release(n: Int): Int {
    var numReleased = 0
    for (i in (0 until n)) {
      val e = pending.poll() ?: return numReleased
      visible.add(e)
      numReleased ++
    }

    return numReleased
  }

  /** releases all items from the pending queue, returning the number of items released */
  fun releaseAll(): Int {
    var numReleased = 0
    while (true) {
      val e = pending.poll() ?: return numReleased
      visible.add(e)
      numReleased ++
    }
  }

  override fun contains(element: T) = visible.contains(element)

  override fun addAll(elements: Collection<T>) = pending.addAll(elements)

  override fun clear() {
    visible.clear()
    pending.clear()
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    val removedFromReleased = visible.removeAll(elements)
    val removedFromPending = pending.removeAll(elements)
    return removedFromReleased || removedFromPending
  }

  override fun add(element: T) = pending.add(element)
  override fun offer(e: T) = pending.offer(e)
  override fun offer(e: T, timeout: Long, unit: TimeUnit) = pending.offer(e, timeout, unit)
  override fun put(e: T) = pending.put(e)
  fun peekPending(): T? = pending.peek()

  override fun element(): T = visible.element()
  override fun take(): T = visible.take()
  override fun iterator() = visible.iterator()
  override fun peek(): T? = visible.peek()
  override fun isEmpty() = visible.isEmpty()
  override fun poll(timeout: Long, unit: TimeUnit): T? = visible.poll(timeout, unit)
  override fun poll(): T? = visible.poll()
  override fun drainTo(c: MutableCollection<in T>) = visible.drainTo(c)
  override fun drainTo(c: MutableCollection<in T>?, maxElements: Int) =
      visible.drainTo(c, maxElements)

  override fun remove(element: T): Boolean {
    val removedFromReleased = visible.remove(element)
    val removedFromPending = pending.remove(element)
    return removedFromReleased || removedFromPending
  }

  override fun remove(): T? = visible.remove() ?: pending.remove()

  override fun containsAll(elements: Collection<T>): Boolean {
    throw UnsupportedOperationException()
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    val visibleChanged = visible.retainAll(elements)
    val pendingChanged = pending.retainAll(elements)
    return visibleChanged || pendingChanged
  }

  override fun remainingCapacity(): Int = pending.remainingCapacity()
  override val size: Int get() = visible.size + pending.size
}
