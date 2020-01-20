package misk.queuing

import org.junit.jupiter.api.Test
import java.util.ArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TimedBlockingQueueTest {
  private val queueSize = 10

  @Test fun shouldCallDelayHandlerForTake() {
    val delays = ArrayList<Long>()
    val queue = TimedBlockingQueue<String>(queueSize){ delay: Long -> delays.add(delay)}
    val beforeStartTime = System.currentTimeMillis()
    queue.add("test")
    assertEquals(1, queue.size)
    assertTrue(delays.isEmpty())
    val item = queue.take()
    assertEquals("test", item)
    assertTrue(queue.isEmpty())
    assertEquals(1, delays.size)
    assertTrue(delays.get(0) > beforeStartTime)
  }

  @Test fun shouldCallDelayHandlerForRemove() {
    val delays = ArrayList<Long>()
    val queue = TimedBlockingQueue<String>(queueSize){ delay: Long -> delays.add(delay)}
    queue.add("test")
    assertEquals(1, queue.size)
    assertTrue(delays.isEmpty())
    val item = queue.remove()
    assertEquals("test", item)
    assertTrue(queue.isEmpty())
    assertEquals(1, delays.size)
  }

  @Test fun shouldCallDelayHandlerForPoll() {
    val delays = ArrayList<Long>()
    val queue = TimedBlockingQueue<String>(queueSize){ delay: Long -> delays.add(delay)}
    queue.add("test")
    assertEquals(1, queue.size)
    assertTrue(delays.isEmpty())
    val item = queue.poll()
    assertEquals("test", item)
    assertTrue(queue.isEmpty())
    assertEquals(1, delays.size)
  }

  // TODO add more tests
}