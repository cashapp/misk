package misk.queuing

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.ArrayList
import java.util.NoSuchElementException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class TimedBlockingQueueTest {
  private val queueSize = 10

  @Test fun shouldCallDelayHandlerAndRemoveSingleItem() {
    val value = 1
    val delays = ArrayList<Long>()
    val queue = TimedBlockingQueue<Int>(
      queueSize
    ) { delay: Duration -> delays.add(delay.toMillis()) }
    val removeList = listOf(
      { queue.take() },
      { queue.remove() },
      { queue.poll() },
      { queue.poll(1, TimeUnit.SECONDS) },
      { queue.remove(value) }
    )
    for (lambda in removeList) {
      assertTrue(queue.isEmpty())
      queue.add(value)
      assertEquals(1, queue.size)
      assertEquals(value, queue.element())
      assertTrue(delays.isEmpty())
      lambda()
      assertTrue(queue.isEmpty())
      assertEquals(1, delays.size)
      assertTrue(delays[0] >= 0)
      delays.clear()
    }
  }

  @Test fun shouldCallDelayHandlerAndRemoveMultipleItems() {
    val values = listOf(1, 2)
    val delays = ArrayList<Long>()
    val queue = TimedBlockingQueue<Int>(
      queueSize
    ) { delay: Duration -> delays.add(delay.toMillis()) }
    val removeList = listOf(
      { queue.removeAll(values) },
      { queue.retainAll(listOf(0)) },
      { queue.drainTo(mutableListOf()) },
      { queue.drainTo(mutableListOf(), 2) },
      { queue.clear() }
    )
    for (lambda in removeList) {
      assertTrue(queue.isEmpty())
      queue.addAll(values)
      assertEquals(values[0], queue.peek())
      assertEquals(2, queue.size)
      assertTrue(queue.containsAll(values))
      assertTrue(delays.isEmpty())
      lambda()
      assertTrue(queue.isEmpty())
      assertEquals(2, delays.size)
      assertTrue(delays[0] >= 0)
      assertTrue(delays[1] >= 0)
      delays.clear()
    }
  }

  @Test fun shouldAddSingleItem() {
    val value = 1
    val delays = ArrayList<Long>()
    val queue = TimedBlockingQueue<Int>(
      queueSize
    ) { delay: Duration -> delays.add(delay.toMillis()) }
    val addList = listOf(
      { queue.add(value) },
      { queue.put(value) },
      { queue.offer(value) },
      { queue.offer(value, 1, TimeUnit.SECONDS) }
    )
    for (lambda in addList) {
      assertTrue(queue.isEmpty())
      lambda()
      assertEquals(1, queue.size)
      assertEquals(value, queue.element())
      queue.clear()
    }
  }

  @Test fun readOperationsShouldBehaveExactlyAsArrayBlockingQueue() {
    val values = listOf(1, 2, 3, 4, 5, 6, 7)
    val delays = ArrayList<Long>()
    val timedQueue = TimedBlockingQueue<Int>(
      queueSize
    ) { delay: Duration -> delays.add(delay.toMillis()) }
    val arrayQueue = ArrayBlockingQueue<Int>(queueSize)
    timedQueue.addAll(values)
    arrayQueue.addAll(values)

    // read/remove operations on non-empty queue
    val timedQueueIterator = timedQueue.iterator()
    val arrayQueueIterator = arrayQueue.iterator()
    while (timedQueueIterator.hasNext()) {
      assertEquals(arrayQueueIterator.next(), timedQueueIterator.next())
    }
    assertFalse(arrayQueueIterator.hasNext())

    for (value in values) {
      assertEquals(arrayQueue.contains(value), timedQueue.contains(value))
    }
    assertEquals(arrayQueue.contains(0), timedQueue.contains(0))

    assertEquals(arrayQueue.isEmpty(), timedQueue.isEmpty())
    assertEquals(arrayQueue.remainingCapacity(), timedQueue.remainingCapacity())
    assertEquals(arrayQueue.peek(), timedQueue.peek())
    assertEquals(arrayQueue.element(), timedQueue.element())
    assertEquals(arrayQueue.remove(), timedQueue.remove())
    assertEquals(arrayQueue.poll(), timedQueue.poll())
    assertEquals(arrayQueue.poll(1, TimeUnit.SECONDS), timedQueue.poll(1, TimeUnit.SECONDS))
    assertEquals(arrayQueue.remove(4), timedQueue.remove(4))
    assertEquals(arrayQueue.take(), timedQueue.take())
    assertEquals(arrayQueue.removeAll(values), timedQueue.removeAll(values))
    // read/remove operations on empty queue
    assertEquals(arrayQueue.isEmpty(), timedQueue.isEmpty())
    assertEquals(arrayQueue.remainingCapacity(), timedQueue.remainingCapacity())
    assertEquals(arrayQueue.peek(), timedQueue.peek())
    assertFailsWith(NoSuchElementException::class) { timedQueue.element() }
    assertFailsWith(NoSuchElementException::class) { timedQueue.remove() }
    assertEquals(arrayQueue.poll(), timedQueue.poll())
    assertEquals(arrayQueue.poll(1, TimeUnit.SECONDS), timedQueue.poll(1, TimeUnit.SECONDS))
  }

  @Test fun draintToShouldAppendToInput() {
    val values = listOf(1, 2, 3)
    val queue = TimedBlockingQueue<Int>(queueSize) { }
    queue.addAll(values)
    val mutableList = mutableListOf<Int>()
    queue.drainTo(mutableList)
    assertTrue(queue.isEmpty())
    assertEquals(values, mutableList)

    mutableList.clear()
    queue.addAll(values)
    queue.drainTo(mutableList, 2)
    assertEquals(1, queue.size)
    assertEquals(listOf(1, 2), mutableList)
  }

  @Test fun nullReturnsShouldNotThrow() {
    val queue = TimedBlockingQueue<Int>(queueSize) { }
    assertNull(queue.poll())
    assertNull(queue.poll(1, TimeUnit.SECONDS))
  }
}
