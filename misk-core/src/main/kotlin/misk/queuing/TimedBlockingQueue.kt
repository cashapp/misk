package misk.queuing

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * This BlockingQueue implementation allows measuring how long dequeued items have been
 * in the queue. It requires a delayHandler which consumes the latency every time an item is
 * removed from the queue by {@link BlockingQueue#take()},
 * {@link BlockingQueue#poll(long, TimeUnit)},
 * {@link BlockingQueue#poll()} or
 * {@link BlockingQueue#remove()}.
 * The operations
 * {@link BlockingQueue#drainTo(Collection)} and
 * {@link BlockingQueue#drainTo(Collection, int)} are not supported
 * @param <T> The type of the elements held in this queue
 */
class TimedBlockingQueue<T>(
  maxQueueSize: Int,
  private val delayHandler: (Long) -> Unit
) : BlockingQueue<T> {
  private val queue: BlockingQueue<TimedQueueItem<T>> =
      ArrayBlockingQueue<TimedQueueItem<T>>(maxQueueSize)

  override fun poll(timeout: Long, unit: TimeUnit): T? {
    val item = queue.poll(timeout, unit)
    return if (item != null) handleRemoved(item) else null
  }

  override fun poll(): T? {
    val item = queue.poll()
    return if (item != null) handleRemoved(item) else null
  }

  override fun take(): T {
    return handleRemoved(queue.take())
  }

  override fun remove(): T {
    return handleRemoved(queue.remove())
  }

  override fun contains(element: T): Boolean {
    return queue.contains(wrap(element))
  }

  override fun addAll(elements: Collection<T>): Boolean {
    return queue.addAll(wrapCollection(elements))
  }

  override fun clear() {
    queue.clear()
  }

  override fun element(): T {
    return unwrap(queue.element())
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    return queue.removeAll(wrapCollection(elements))
  }

  override fun add(element: T): Boolean {
    return queue.add(wrap(element))
  }

  override fun offer(e: T): Boolean {
    return queue.offer(wrap(e))
  }

  override fun offer(e: T, timeout: Long, unit: TimeUnit): Boolean {
    return queue.offer(wrap(e), timeout, unit)
  }

  override fun iterator(): MutableIterator<T> {
    return queue.map(this::unwrap).toMutableList().iterator()
  }

  override fun peek(): T {
    return unwrap(queue.peek())
  }

  override fun put(e: T) {
    queue.put(wrap(e))
  }

  override fun isEmpty(): Boolean {
    return queue.isEmpty()
  }

  override fun remove(element: T): Boolean {
    return queue.remove(wrap(element))
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    return queue.containsAll(wrapCollection(elements))
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    return queue.retainAll(wrapCollection(elements))
  }

  override fun remainingCapacity(): Int {
    return queue.remainingCapacity()
  }

  override fun drainTo(c: MutableCollection<in T>): Int {
    throw UnsupportedOperationException()
  }

  override fun drainTo(c: MutableCollection<in T>, maxElements: Int): Int {
    throw UnsupportedOperationException()
  }

  override val size: Int
    get() = queue.size

  private fun <E> wrap(obj: E): TimedQueueItem<E> {
    return TimedQueueItem(obj, System.currentTimeMillis())
  }

  private fun unwrap(item: TimedQueueItem<T>): T {
    return item.value
  }

  private fun handleRemoved(item: TimedQueueItem<T>): T {
    delayHandler(System.currentTimeMillis() - item.startTime)
    return unwrap(item)
  }

  private fun wrapCollection(collection: Collection<T>): Collection<TimedQueueItem<T>> {
    return collection.map(this::wrap)
  }

}