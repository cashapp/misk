package misk.concurrent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.time.FakeClock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FakeSleeperTest {
  @Test
  fun testSleep() {
    val clock = FakeClock()
    val sleeper = FakeSleeper(clock)
    val awake = CountDownLatch(1)
    Thread {
      sleeper.sleep(Duration.ofMillis(1000))
      awake.countDown()
    }.start()

    sleeper.waitForSleep(1)

    clock.add(Duration.ofMillis(1000))
    sleeper.tick()
    assertThat(awake.await(1L, TimeUnit.SECONDS)).isTrue()
    assertThat(sleeper.sleepCount()).isEqualTo(1)
    assertThat(sleeper.lastSleepDuration()).isEqualTo(Duration.ofMillis(1000))
  }

  @Test
  fun testConcurrentSleep() {
    val clock = FakeClock()
    val sleeper = FakeSleeper(clock)
    val awake = CountDownLatch(2)
    Thread {
      sleeper.sleep(Duration.ofMillis(1000))
      awake.countDown()
    }.start()
    Thread {
      sleeper.sleep(Duration.ofMillis(1000))
      awake.countDown()
    }.start()

    sleeper.waitForSleep(2)

    clock.add(Duration.ofMillis(1000))
    sleeper.tick()
    assertThat(awake.await(1L, TimeUnit.SECONDS)).isTrue()
    assertThat(sleeper.sleepCount()).isEqualTo(2)
    assertThat(sleeper.lastSleepDuration()).isEqualTo(Duration.ofMillis(1000))
  }

  @Test
  fun testSequentialWake() {
    val clock = FakeClock()
    val sleeper = FakeSleeper(clock)
    val awake1 = CountDownLatch(1)
    val awake2 = CountDownLatch(1)
    Thread {
      sleeper.sleep(Duration.ofMillis(1000))
      awake1.countDown()
    }.start()
    Thread {
      sleeper.sleep(Duration.ofMillis(2000))
      awake2.countDown()
    }.start()

    sleeper.waitForSleep(2)
    clock.add(Duration.ofMillis(1000))
    sleeper.tick()

    assertThat(awake1.await(1L, TimeUnit.SECONDS)).isTrue()

    sleeper.waitForSleep(1)
    clock.add(Duration.ofMillis(1000))
    sleeper.tick()

    assertThat(awake2.await(1L, TimeUnit.SECONDS)).isTrue()
    assertThat(sleeper.sleepCount()).isEqualTo(2)
  }
}
