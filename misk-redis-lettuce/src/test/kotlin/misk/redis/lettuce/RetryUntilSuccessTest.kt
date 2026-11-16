package misk.redis.lettuce

import io.kotest.assertions.nondeterministic.continually
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import misk.backoff.FlatBackoff
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class RetryUntilSuccessTest {
  private val completer = Executors.newSingleThreadExecutor()

  @AfterEach
  fun tearDown() {
    completer.shutdownNow()
  }

  @Test
  fun `completes after many failures`() {
    val attempts = AtomicInteger()
    val result =
      retryUntilSuccess(FlatBackoff(Duration.ZERO)) {
        CompletableFuture.supplyAsync(
          { if (attempts.incrementAndGet() <= 20_000) throw IllegalStateException("unreachable") else "connected" },
          completer,
        )
      }

    assertEquals("connected", result.get(60, TimeUnit.SECONDS))
  }

  @Test
  fun `fails without retrying when shouldRetry rejects the error`() {
    var attempts = 0
    val result =
      retryUntilSuccess<String>(FlatBackoff(Duration.ZERO), shouldRetry = { it !is IllegalArgumentException }) {
        attempts++
        CompletableFuture.failedFuture(IllegalArgumentException("bad script"))
      }

    val e = assertFailsWith<ExecutionException> { result.get(10, TimeUnit.SECONDS) }
    assertIs<IllegalArgumentException>(e.cause)
    assertEquals(1, attempts)
  }

  @Test
  fun `doesn't start the next attempt once the result times out during backoff`(): Unit = runBlocking {
    val attempts = AtomicInteger()
    val result =
      retryUntilSuccess<String>(FlatBackoff(Duration.ofMillis(200))) {
          attempts.incrementAndGet()
          CompletableFuture.failedFuture(IllegalStateException("unreachable"))
        }
        .orTimeout(10, TimeUnit.MILLISECONDS)

    val e = assertFailsWith<ExecutionException> { result.get(10, TimeUnit.SECONDS) }
    assertIs<TimeoutException>(e.cause)
    continually(500.milliseconds) { assertEquals(1, attempts.get()) }
  }
}
