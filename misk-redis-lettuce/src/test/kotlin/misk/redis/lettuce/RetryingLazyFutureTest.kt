package misk.redis.lettuce

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import misk.backoff.FlatBackoff
import org.junit.jupiter.api.Test

internal class RetryingLazyFutureTest {
  private var creations = 0

  private fun failOnce() = {
    creations++
    if (creations == 1) CompletableFuture.failedFuture(RuntimeException("unreachable"))
    else CompletableFuture.completedFuture("connected")
  }

  @Test
  fun `retries a failed creation after the backoff and memoizes the success`() {
    val future = RetryingLazyFuture(FlatBackoff(Duration.ZERO), failOnce())

    assertFailsWith<ExecutionException> { future.get().get() }
    assertEquals("connected", future.get().get())
    assertEquals("connected", future.get().get())
    assertEquals(2, creations)
  }

  @Test
  fun `returns the failure without retrying until the backoff elapses`() {
    val future = RetryingLazyFuture(FlatBackoff(Duration.ofHours(1)), failOnce())

    assertFailsWith<ExecutionException> { future.get().get() }
    assertFailsWith<ExecutionException> { future.get().get() }
    assertEquals(1, creations)
  }
}
