package misk.redis.lettuce

import io.lettuce.core.api.AsyncCloseable
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Ensures that the [AsyncCloseable] is closed when the [CompletionStage] completes, regardless of whether
 * the completion is successful or exceptional.
 *
 * This is the asynchronous equivalent of using a resource within a try-finally block where the resource
 * is closed in the finally block.
 *
 * Example:
 * ```kotlin
 * val result = someAsyncOperation()
 *   .closeOnCompletion(connection)
 * ```
 *
 * @param c The [AsyncCloseable] resource to be closed when the [CompletionStage] completes
 * @return The original [CompletionStage] with the close operation attached
 */
fun <T : AsyncCloseable, R> CompletionStage<R>.closeOnCompletion(c: T): CompletionStage<R> =
  whenComplete { _, t -> c.closeFinallyAsync(t) }

/**
 * Executes the given [block] function with the [AsyncCloseable] resource as its argument and ensures
 * the resource is closed when the resulting [CompletionStage] completes.
 *
 * This is the asynchronous equivalent of the standard library's [use] function for [java.io.Closeable],
 * but adapted for [CompletionStage] composition with [AsyncCloseable] resources.
 *
 * Example:
 * ```kotlin
 * connectionProvider.getConnectionAsync()
 *   .thenComposeUsing { connection ->
 *     connection.async().get("key")
 *   }
 * ```
 *
 * @param block A function that takes the resource and returns a [CompletionStage]
 * @return A [CompletionStage] that will complete with the result of the [block] and ensure the resource is closed
 */
inline fun <T : AsyncCloseable, R> CompletionStage<T>.thenComposeUsing(
  crossinline block: (T) -> CompletionStage<R>
): CompletionStage<R> = thenCompose {
  block(it)
    .closeOnCompletion(it)
}

/**
 * Executes the given [block] function with the [AsyncCloseable] resource as its argument and ensures
 * the resource is closed when the resulting [CompletionStage] completes.
 *
 * Similar to [thenComposeUsing], but for synchronous operations that don't return a [CompletionStage].
 * The result of [block] is wrapped in a completed [CompletionStage].
 *
 * This is analogous to the standard library's [use] function for [java.io.Closeable], but adapted for
 * [CompletionStage] composition with [AsyncCloseable] resources.
 *
 * Example:
 * ```kotlin
 * connectionProvider.getConnectionAsync()
 *   .thenApplyUsing { connection ->
 *     connection.sync().get("key")
 *   }
 * ```
 *
 * @param block A function that takes the resource and returns a result of type [R]
 * @return A [CompletionStage] that will complete with the result of the [block] and ensure the resource is closed
 */
inline fun <T : AsyncCloseable, R> CompletionStage<T>.thenApplyUsing(
  crossinline block: (T) -> R
): CompletionStage<R> = thenComposeUsing {
  runCatching { (block(it)) }.fold(
    onSuccess = { CompletableFuture.completedFuture(it) },
    onFailure = { CompletableFuture.failedFuture(it) },
  )
}

/**
 * Executes the given [block] function with the [AsyncCloseable] resource as its argument and ensures
 * the resource is properly closed when the block completes, whether successfully or with an exception.
 *
 * This is the direct coroutine equivalent of the standard library's [use] function for [java.io.Closeable],
 * but adapted for suspending functions with [AsyncCloseable] resources.
 *
 * Example:
 * ```kotlin
 * connection.suspendingUse { conn ->
 *   conn.coroutines().get("key")
 * }
 * ```
 *
 * @param block A function that takes the resource and returns a result of type [R]
 * @return The result of the [block] function
 * @throws Throwable if the [block] throws an exception
 */
suspend inline fun <T : AsyncCloseable, R> T.suspendingUse(block: (T) -> R): R {
  var exception: Throwable? = null
  return try {
    block(this)
  } catch (e: Throwable) {
    exception = e
    throw e
  } finally {
    closeFinallyAsync(exception).await()
  }
}

/**
 * Internal helper function to close an [AsyncCloseable] resource asynchronously, suppressing any exceptions
 * from the close operation if a primary exception is already present.
 *
 * This is similar to how the standard library's [use] function handles suppressed exceptions when closing resources.
 *
 * @param cause The primary exception that occurred during resource usage, if any
 * @return A [CompletionStage] representing the completion of the close operation
 */
@SinceKotlin("1.2")
@PublishedApi
internal fun AsyncCloseable.closeFinallyAsync(cause: Throwable? = null): CompletionStage<Void> =
  // Suppress any exceptions from closeAsync
  closeAsync().exceptionally { t ->
    cause?.addSuppressed(t)
    // Log the error
    null
  }

