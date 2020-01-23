package misk.queuing

import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * This BlockingQueue implementation allows measuring how long dequeued items have been
 * in the queue. It requires a delayHandler which consumes the latency every time an item is
 * removed from the queue.
 * Note that the the actual removal of an item from the queue and the delayHandler invocation
 * are not atomic, hence it may not be appropriate for applications which require
 * a higher level of accuracy.
 * @param <T> The type of the elements held in this queue
 */
class TimedBlockingQueue<T>(
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
    invokeDelayHandlerOnAll(queue)
    queue.clear()
  }

  override fun element(): T {
    return unwrap(queue.element())
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    return removeItems(elements)
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
    return removeItems(listOf(element))
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    return queue.containsAll(wrapCollection(elements))
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    val toRemove = queue.filter {wrappedItem -> !elements.contains(wrappedItem.value)}
    val result = queue.retainAll(wrapCollection(elements))
    invokeDelayHandlerOnAll(toRemove)
    return result
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
    return TimedQueueItem(obj, System.currentTimeMillis())
  }

  private fun unwrap(item: TimedQueueItem<T>): T {
    return item.value
  }

  private fun invokeDelayHandler(item: TimedQueueItem<T>) {
    delayHandler(Duration.ofMillis(System.currentTimeMillis() - item.startTime))
  }

  private fun handleRemoved(item: TimedQueueItem<T>): T {
    invokeDelayHandler(item)
    return unwrap(item)
  }

  private fun wrapCollection(collection: Collection<T>): Collection<TimedQueueItem<T>> {
    return collection.map(this::wrap)
  }

  private fun find(collection: Collection<T>): Collection<TimedQueueItem<T>> {
    return queue
        .filter {wrappedItem -> collection.contains(wrappedItem.value)}
        .toList()
  }

  private fun invokeDelayHandlerOnAll(collection: Collection<TimedQueueItem<T>>) {
    collection.forEach {item -> invokeDelayHandler(item)}
  }

  private fun removeItems(collection: Collection<T>): Boolean {
    val itemsToRemove = find(collection)
    val result = queue.removeAll(wrapCollection(collection))
    invokeDelayHandlerOnAll(itemsToRemove)
    return result
  }

}