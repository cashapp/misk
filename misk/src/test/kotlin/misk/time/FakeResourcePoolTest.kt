package misk.time

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.InterruptedIOException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.test.assertFailsWith

internal class FakeResourcePoolTest {
  private var executorService: ExecutorService? = null

  @AfterEach
  fun tearDown() {
    executorService?.shutdown()
  }

  @Test
  fun happyPath() {
    val pool = FakeResourcePool()
    pool.total = 1

    assertElapsedTime(expected = Duration.ofMillis(750)) {
      pool.useResource(maxTimeToWait = Duration.ZERO, timeToHold = Duration.ofMillis(250))
      pool.useResource(maxTimeToWait = Duration.ZERO, timeToHold = Duration.ofMillis(250))
      pool.useResource(maxTimeToWait = Duration.ZERO, timeToHold = Duration.ofMillis(250))
    }
  }

  @Test
  fun insufficientResources() {
    val pool = FakeResourcePool()
    pool.total = 0

    assertFailsWith<InterruptedIOException> {
      pool.useResource(maxTimeToWait = Duration.ZERO, timeToHold = Duration.ofMillis(250))
    }
  }

  /** Run 15 tasks over 5 threads targeting 3 resources. */
  @Test
  fun successfulContention() {
    val pool = FakeResourcePool()
    pool.total = 3

    executorService = Executors.newFixedThreadPool(5)

    assertElapsedTime(expected = Duration.ofMillis(1250)) {
      val futures = mutableListOf<Future<*>>()
      for (i in 0 until 15) {
        futures += executorService!!.submit {
          pool.useResource(
              maxTimeToWait = Duration.ofMillis(1100),
              timeToHold = Duration.ofMillis(250)
          )
        }
      }
      for (future in futures) {
        future.get()
      }
    }
  }

  /**
   * Run 9 tasks over 9 threads targeting 3 resources:
   *
   *  * 3 will complete immediately
   *  * 3 will complete after waiting
   *  * 3 will time out.
   */
  @Test
  fun contentionTimeouts() {
    val pool = FakeResourcePool()
    pool.total = 3

    executorService = Executors.newFixedThreadPool(9)

    assertElapsedTime(expected = Duration.ofMillis(500)) {
      val futures = mutableListOf<Future<*>>()
      for (i in 0 until 9) {
        futures += executorService!!.submit {
          pool.useResource(
              maxTimeToWait = Duration.ofMillis(400),
              timeToHold = Duration.ofMillis(250)
          )
        }
      }

      var successCount = 0
      var failureCount = 0
      for (future in futures) {
        try {
          future.get()
          successCount++
        } catch (_: ExecutionException) {
          failureCount++
        }
      }
      assertThat(successCount).isCloseTo(6, Offset.offset(2))
      assertThat(failureCount).isCloseTo(3, Offset.offset(2))
    }
  }
}
