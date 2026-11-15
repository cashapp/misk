package misk.redis.lettuce

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference
import misk.backoff.Backoff
import misk.backoff.ExponentialBackoff

/**
 * Lazily creates a value asynchronously with [create] and memoizes it once creation succeeds.
 *
 * A failed creation is not memoized forever: the failure is returned until [backoff] elapses, and the next [get] after
 * that starts a new creation. This lets connections that couldn't be established while Redis was unreachable recover
 * once it's reachable again, without reconnecting on every call while it's still down. At most one creation is in
 * flight at a time, and concurrent callers share its result.
 */
internal class RetryingLazyFuture<T>(
  private val backoff: Backoff = ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(5)),
  private val create: () -> CompletionStage<T>,
) {
  private class Attempt<T> {
    val result = CompletableFuture<T>()
    @Volatile var retryAtNanos = 0L

    fun canRetry() = result.isCompletedExceptionally && System.nanoTime() - retryAtNanos >= 0
  }

  private val current = AtomicReference<Attempt<T>?>()

  /** Returns a copy of the result so callers completing or cancelling it don't affect the memoized result. */
  fun get(): CompletableFuture<T> {
    val attempt = current.get()
    if (attempt != null && !attempt.canRetry()) return attempt.result.copy()

    val next = Attempt<T>()
    if (!current.compareAndSet(attempt, next)) return current.get()!!.result.copy()

    val created =
      try {
        create()
      } catch (e: Exception) {
        CompletableFuture.failedFuture(e)
      }
    created.whenComplete { value, error ->
      if (error == null) {
        backoff.reset()
        next.result.complete(value)
      } else {
        next.retryAtNanos = System.nanoTime() + backoff.nextRetry().toNanos()
        next.result.completeExceptionally(error)
      }
    }
    return next.result.copy()
  }

  /** Returns the latest result without starting a creation, or null if [get] has never been called. */
  fun peek(): CompletableFuture<T>? = current.get()?.result?.copy()
}
