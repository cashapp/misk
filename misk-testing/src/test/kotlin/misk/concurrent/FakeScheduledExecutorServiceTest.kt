package misk.concurrent

import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FakeScheduledExecutorServiceTest {
  @Test
  fun testScheduleTask() {
    val clock = FakeClock()
    val executor = FakeScheduledExecutorService(clock)
    val done = CountDownLatch(1)
    val result = 5

    val task = executor.schedule<Int>({
      done.countDown()
      result
    }, 10, TimeUnit.SECONDS)

    clock.add(Duration.ofSeconds(10))
    executor.tick()

    assertThat(done.await(1L, TimeUnit.SECONDS)).isTrue()
    assertThat(task.get()).isEqualTo(result)
  }

  @Test
  fun testScheduleTaskWithFixedDelay() {
    val clock = FakeClock()
    val executor = FakeScheduledExecutorService(clock)
    val done = AtomicInteger(0)

    executor.scheduleWithFixedDelay({
      done.getAndAdd(1)
    }, 10, 10, TimeUnit.MILLISECONDS)

    clock.add(Duration.ofMillis(30))
    executor.tick()

    assertThat(done.get()).isEqualTo(3)
  }
}
