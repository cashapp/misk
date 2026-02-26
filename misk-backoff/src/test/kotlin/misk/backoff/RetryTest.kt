package misk.backoff

import java.time.Duration
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.failedFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class RetryTest {

  @Test
  fun dontRetryExceptionCanBeInstantiatedWithNoMessage() {
    // needed to ensure backwards compat with 2 services
    val exception = DontRetryException()
    assertNotNull(exception)
    assertNull(exception.message)
  }

  @Nested
  inner class SynchronousRetryTest {
    @Test
    fun deprecatedFunction() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      val result =
        retry(3, backoff) { retry ->
          if (retry < 2) throw IllegalStateException("this failed")
          "succeeded on retry $retry"
        }

      assertThat(result).isEqualTo("succeeded on retry 2")
    }

    @Test
    fun retries() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      val retryConfig = RetryConfig.Builder(2, backoff)
      val result =
        retry(retryConfig.build()) { retry: Int ->
          if (retry < 2) throw IllegalStateException("this failed")
          "succeeded on retry $retry"
        }

      assertThat(result).isEqualTo("succeeded on retry 2")
    }

    @Test
    fun failsIfExceedsMaxRetries() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      assertFailsWith<IllegalStateException> {
        val retryConfig = RetryConfig.Builder(2, backoff)
        retry(retryConfig.build()) { throw IllegalStateException("this failed") }
      }
    }

    @Test
    fun honorsBackoff() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      assertFailsWith<IllegalStateException> {
        val retryConfig = RetryConfig.Builder(2, backoff)
        retry(retryConfig.build()) { throw IllegalStateException("this failed") }
      }

      // Backoff should have advanced
      assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(40))
    }

    @Test
    fun resetsBackoffPriorToUse() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      // Preseed the backoff with a delay
      backoff.nextRetry()
      backoff.nextRetry()
      backoff.nextRetry()

      assertFailsWith<IllegalStateException> {
        val retryConfig = RetryConfig.Builder(2, backoff)
        retry(retryConfig.build()) { throw IllegalStateException("this failed") }
      }

      // resets backoff prior to use
      assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(40))
    }

    @Test
    fun resetsBackoffAfterSuccess() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val retryConfig = RetryConfig.Builder(2, backoff)
      val result =
        retry(retryConfig.build()) { retry: Int ->
          if (retry < 2) throw IllegalStateException("this failed")
          "hello"
        }

      assertThat(result).isEqualTo("hello")

      // Backoff should be reset to base delay after success
      assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(10))
    }

    @Test
    fun callsOnRetryCallbackIfProvided() {
      var retryCount = 0
      var retried = 0
      // simply counts the number of times it was called
      val onRetryFunction: (retries: Int, exception: Exception) -> Unit = { _, _ -> retried = retried.inc() }

      // f is a function that throws an exception twice in a row
      val retryConfig = RetryConfig.Builder(3, FlatBackoff()).onRetry(onRetryFunction)
      retry(retryConfig.build()) {
        retryCount = retryCount.inc()
        if (retryCount < 3) throw Exception("a failure that triggers a retry")
      }

      assertThat(retried).isEqualTo(2)
    }

    @Test
    fun throwsDontRetryExceptionWithMessage() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val customMessage = "Custom message for DontRetryException"
      assertFailsWith<DontRetryException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retry(retryConfig.build()) { throw DontRetryException(customMessage) }
        }
        .also { exception -> assertThat(exception.message).isEqualTo(customMessage) }
    }

    @Test
    fun throwsDontRetryExceptionWithCause() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val cause = IllegalStateException("Underlying exception")
      assertFailsWith<DontRetryException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retry(retryConfig.build()) { throw DontRetryException(cause) }
        }
        .also { exception -> assertThat(exception.cause).isEqualTo(cause) }
    }

    @Test
    fun throwsDontRetryExceptionWithMessageAndCause() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val customMessage = "Custom message for DontRetryException"
      val cause = IllegalStateException("Underlying exception")
      assertFailsWith<DontRetryException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retry(retryConfig.build()) { throw DontRetryException(customMessage, cause) }
        }
        .also { exception ->
          assertThat(exception.message).isEqualTo(customMessage)
          assertThat(exception.cause).isEqualTo(cause)
        }
    }

    @Test
    fun dontRetryIfShouldRetryIsFalse() {
      var retryCount = 0

      assertFailsWith<IllegalStateException> {
        retry(RetryConfig.Builder(100, FlatBackoff()).shouldRetry { false }.build()) {
          retryCount = retryCount.inc()
          throw IllegalStateException()
        }
      }

      assertThat(retryCount).isEqualTo(1)
    }

    @Test
    fun dontRetryIfShouldRetryReturnsFalseOnSecondRetry() {
      var retryCount = 0

      assertFailsWith<IllegalStateException> {
        retry(RetryConfig.Builder(100, FlatBackoff()).shouldRetry { e -> e is IllegalArgumentException }.build()) {
          retryCount = retryCount.inc()
          if (retryCount == 1) throw IllegalArgumentException() // We want to retry on this error
          throw IllegalStateException()
        }
      }

      assertThat(retryCount).isEqualTo(2)
    }
  }

  @Nested
  inner class AsynchronousRetryTest {

    @Test
    fun retries() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      val retryConfig = RetryConfig.Builder(2, backoff)
      val result =
        retryableFuture(retryConfig.build()) { retry: Int ->
            if (retry < 2) {
              failedFuture(IllegalStateException("this failed"))
            } else {
              completedFuture("succeeded on retry $retry")
            }
          }
          .get()
      assertThat(result).isEqualTo("succeeded on retry 2")
    }

    @Test
    fun failsIfExceedsMaxRetries() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      assertFailsWith<ExecutionException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retryableFuture(retryConfig.build()) { failedFuture<Void>(IllegalStateException("this failed")) }.get()
        }
        .also { e -> assertTrue(e.cause is IllegalStateException) }
    }

    @Test
    fun honorsBackoff() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      assertFailsWith<ExecutionException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retryableFuture(retryConfig.build()) { failedFuture<Void>(IllegalStateException("this failed")) }.get()
        }
        .also { e -> assertTrue(e.cause is IllegalStateException) }

      // Backoff should have advanced
      assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(40))
    }

    @Test
    fun resetsBackoffPriorToUse() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

      // Preseed the backoff with a delay
      backoff.nextRetry()
      backoff.nextRetry()
      backoff.nextRetry()

      assertFailsWith<ExecutionException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retryableFuture(retryConfig.build()) { failedFuture<Void>(IllegalStateException("this failed")) }.get()
        }
        .also { e -> assertTrue(e.cause is IllegalStateException) }

      // resets backoff prior to use
      assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(40))
    }

    @Test
    fun resetsBackoffAfterSuccess() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val retryConfig = RetryConfig.Builder(2, backoff)
      val result =
        retryableFuture(retryConfig.build()) { retry: Int ->
            if (retry < 2) {
              failedFuture(IllegalStateException("this failed"))
            } else {
              completedFuture("hello")
            }
          }
          .get()

      assertThat(result).isEqualTo("hello")

      // Backoff should be reset to base delay after success
      assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(10))
    }

    @Test
    fun callsOnRetryCallbackIfProvided() {
      var retryCount = 0
      var retried = 0
      // simply counts the number of times it was called
      val onRetryFunction: (retries: Int, exception: Exception) -> Unit = { _, _ -> retried = retried.inc() }

      // f is a function that throws an exception twice in a row
      val retryConfig = RetryConfig.Builder(3, FlatBackoff()).onRetry(onRetryFunction)
      retryableFuture(retryConfig.build()) {
          retryCount = retryCount.inc()
          if (retryCount < 3) {
            failedFuture<Void>(Exception("a failure that triggers a retry"))
          } else {
            completedFuture(null)
          }
        }
        .join()

      assertThat(retried).isEqualTo(2)
    }

    @Test
    fun throwsDontRetryExceptionWithMessage() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val customMessage = "Custom message for DontRetryException"
      assertFailsWith<ExecutionException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retryableFuture(retryConfig.build()) { failedFuture<Void>(DontRetryException(customMessage)) }.get()
        }
        .also { exception ->
          assertThat(exception.cause).isInstanceOf(DontRetryException::class.java)
          val dontRetryException = exception.cause as DontRetryException
          assertThat(dontRetryException.message).isEqualTo(customMessage)
        }
    }

    @Test
    fun throwsDontRetryExceptionWithCause() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val cause = IllegalStateException("Underlying exception")
      assertFailsWith<ExecutionException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retryableFuture(retryConfig.build()) { failedFuture<Void>(DontRetryException(cause)) }.get()
        }
        .also { exception ->
          assertThat(exception.cause).isInstanceOf(DontRetryException::class.java)
          val dontRetryException = exception.cause as DontRetryException
          assertThat(dontRetryException.cause).isEqualTo(cause)
        }
    }

    @Test
    fun throwsDontRetryExceptionWithMessageAndCause() {
      val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      val customMessage = "Custom message for DontRetryException"
      val cause = IllegalStateException("Underlying exception")
      assertFailsWith<ExecutionException> {
          val retryConfig = RetryConfig.Builder(2, backoff)
          retryableFuture(retryConfig.build()) { failedFuture<Void>(DontRetryException(customMessage, cause)) }.get()
        }
        .also { exception ->
          assertThat(exception.cause).isInstanceOf(DontRetryException::class.java)
          val dontRetryException = exception.cause as DontRetryException
          assertThat(dontRetryException.message).isEqualTo(customMessage)
          assertThat(dontRetryException.cause).isEqualTo(cause)
        }
    }

    @Test
    fun dontRetryIfShouldRetryIsFalse() {
      var retryCount = 0

      assertFailsWith<ExecutionException> {
        retryableFuture(RetryConfig.Builder(100, FlatBackoff()).shouldRetry { false }.build()) {
            retryCount = retryCount.inc()
            failedFuture<Void>(IllegalStateException())
          }
          .get()
      }

      assertThat(retryCount).isEqualTo(1)
    }

    @Test
    fun dontRetryIfShouldRetryReturnsFalseOnSecondRetry() {
      var retryCount = 0

      assertFailsWith<ExecutionException> {
          retryableFuture(
              RetryConfig.Builder(100, FlatBackoff()).shouldRetry { e -> e is IllegalArgumentException }.build()
            ) {
              retryCount = retryCount.inc()
              if (retryCount == 1) {
                failedFuture<Void>(IllegalArgumentException()) // We want to retry on this error
              } else {
                failedFuture(IllegalStateException())
              }
            }
            .get()
        }
        .also { exception -> assertThat(exception.cause).isInstanceOf(IllegalStateException::class.java) }

      assertThat(retryCount).isEqualTo(2)
    }
  }

  @Test
  fun willProperlyTimeout() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

    assertFailsWith<ExecutionException> {
        // Use a large but not Int.MAX_VALUE to avoid overflow in totalAttempts calculation
        val retryConfig = RetryConfig.Builder(1000, backoff)
        retryableFuture(retryConfig.build()) { failedFuture<Void>(IllegalStateException("this failed")) }
          .orTimeout(50, TimeUnit.MILLISECONDS)
          .get()
      }
      .also { e -> assertTrue(e.cause is TimeoutException) }
  }
}
