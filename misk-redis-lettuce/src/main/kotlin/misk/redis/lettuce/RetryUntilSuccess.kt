package misk.redis.lettuce

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import misk.backoff.Backoff

/**
 * Calls [attempt] until it succeeds, waiting [backoff] after each failure, and returns a future of the first success.
 *
 * Unlike `misk.backoff.retryableFuture`, each retry is scheduled as a fresh attempt instead of being chained onto the
 * previous one, so completing the result doesn't recurse once per failure and can't overflow the stack after a long
 * outage. Retrying stops, failing the result with the last error, when [shouldRetry] returns false for it. It also
 * stops once the result is completed some other way, for example by [CompletableFuture.orTimeout], skipping any attempt
 * still waiting out its backoff.
 */
internal fun <T> retryUntilSuccess(
  backoff: Backoff,
  shouldRetry: (Throwable) -> Boolean = { true },
  onRetry: (failures: Int, error: Throwable) -> Unit = { _, _ -> },
  attempt: () -> CompletionStage<T>,
): CompletableFuture<T> {
  val result = CompletableFuture<T>()

  fun tryOnce(failures: Int) {
    if (result.isDone) return
    val stage =
      try {
        attempt()
      } catch (e: Exception) {
        CompletableFuture.failedFuture(e)
      }
    stage.whenComplete { value, completionError ->
      val error = (completionError as? CompletionException)?.cause ?: completionError
      when {
        error == null -> result.complete(value)
        result.isDone -> Unit
        !shouldRetry(error) -> result.completeExceptionally(error)
        else -> {
          onRetry(failures + 1, error)
          CompletableFuture.delayedExecutor(backoff.nextRetry().toMillis(), TimeUnit.MILLISECONDS).execute {
            tryOnce(failures + 1)
          }
        }
      }
    }
  }

  tryOnce(0)
  return result
}
