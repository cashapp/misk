package misk.queuing

import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * This BlockingQueue implementation allows measuring how long dequeued items have been
 * in the queue. It requires a delayHandler which consumes the latency every time an item is
 * removed from the queue.
 *
 * Caveat: The operations remove(element), removeAll() and retainAll() are supported but may not
 * always invoke the delayHandler in a multithreaded environment. For removing items, poll(),
 * take() and remove() should be used instead.
 *
 * @param <T> The type of the elements held in this queue
 */
internal class TimedBlockingQueue<T>(
  maxQueueSize: Int,
  private val delayHandler: (Duration) -> Unit
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
    var result = poll()
    while (result != null) {
      result = poll()
    }
  }

  override fun element(): T {
    return unwrap(queue.element())
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    return removeItems(elements) > 0
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

  override fun peek(): T? {
    val item = queue.peek()
    if (item != null) {
      return unwrap(item)
    }
    return null
  }

  override fun put(e: T) {
    queue.put(wrap(e))
  }

  override fun isEmpty(): Boolean {
    return queue.isEmpty()
  }

  override fun remove(element: T): Boolean {
    return removeItem(element)
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    return queue.containsAll(wrapCollection(elements))
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    val toRemove = queue
      .filter { wrappedItem -> !elements.contains(wrappedItem.value) }
      .map { item -> item.value }
    return removeItems(toRemove) > 0
  }

  override fun remainingCapacity(): Int {
    return queue.remainingCapacity()
  }

  override fun drainTo(c: MutableCollection<in T>): Int {
    val collection = mutableListOf<TimedQueueItem<T>>()
    val result = queue.drainTo(collection)
    c.addAll(collection.map(this::unwrap))
    invokeDelayHandlerOnAll(collection)
    return result
  }

  override fun drainTo(c: MutableCollection<in T>, maxElements: Int): Int {
    val collection = mutableListOf<TimedQueueItem<T>>()
    val result = queue.drainTo(collection, maxElements)
    c.addAll(collection.map(this::unwrap))
    invokeDelayHandlerOnAll(collection)
    return result
  }

  override val size: Int
    get() = queue.size

  private fun <E> wrap(obj: E): TimedQueueItem<E> {
    return TimedQueueItem(obj, System.nanoTime())
  }

  private fun unwrap(item: TimedQueueItem<T>): T {
    return item.value
  }

  private fun invokeDelayHandler(item: TimedQueueItem<T>) {
    delayHandler(Duration.ofNanos(System.nanoTime() - item.startTime))
  }

  private fun handleRemoved(item: TimedQueueItem<T>): T {
    invokeDelayHandler(item)
    return unwrap(item)
  }

  private fun wrapCollection(collection: Collection<T>): Collection<TimedQueueItem<T>> {
    return collection.map(this::wrap)
  }

  private fun invokeDelayHandlerOnAll(collection: Collection<TimedQueueItem<T>>) {
    collection.forEach { item -> invokeDelayHandler(item) }
  }

  private fun removeItems(collection: Collection<T>): Int {
    var count = 0
    for (element in collection) {
      if (removeItem(element)) {
        count++
      }
    }
    return count
  }

  private fun removeItem(element: T): Boolean {
    val foundItem = queue.find { item -> item == wrap(element) }
    // if 'element' was added after find() and before remove(), the delayHandler will
    // not be invoked
    val removed = queue.remove(wrap(element))
    if (removed && foundItem != null) {
      invokeDelayHandler(foundItem)
    }
    return removed
  }
}
