package misk.web

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible

/**
 * Runs the blocking writes of suspending streaming responses: server-sent events and gRPC server streaming.
 *
 * A write blocks while the client isn't reading. Running it here instead of on the action's `runBlocking` event loop
 * leaves that loop free to process deadlines and cancellation. Each in-flight streaming call holds one Jetty thread and
 * has at most one write in progress, so sizing this to the Jetty thread pool means a write never waits for a thread.
 */
@Singleton
internal class StreamingResponseWriteDispatcher @Inject constructor(webConfig: WebConfig) {
  val dispatcher: CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(
      parallelism = webConfig.jetty_max_thread_pool_size,
      name = "StreamingResponseWriteDispatcher",
    )
}

/**
 * Runs a blocking response [write] on [context], interrupting it if the calling coroutine is cancelled.
 *
 * Jetty reports an interrupted write as an [InterruptedIOException], which [runInterruptible] doesn't treat as
 * cancellation. Rethrow the caller's cancellation instead, so the call fails with the cause, such as a deadline, rather
 * than with an IO error.
 */
internal suspend fun <T> runInterruptibleWrite(context: CoroutineContext, write: () -> T): T =
  try {
    runInterruptible(context, write)
  } catch (e: InterruptedIOException) {
    currentCoroutineContext().ensureActive()
    throw e
  }
