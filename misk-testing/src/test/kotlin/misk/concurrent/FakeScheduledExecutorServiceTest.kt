package misk.concurrent

import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
}