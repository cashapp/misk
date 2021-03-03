package misk.concurrent

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Inject this rather than using the [Executors] factory class to create thread pools. Executors
 * created with this factory will automatically be shut down when the service or test completes.
 *
 * For all functions, `nameFormat` a string in the format specified by
 * [ThreadFactoryBuilder.setNameFormat], like "rpc-pool-%d". If the string has a single `%d`
 * placeholder it will be assigned sequentially. Omit the placeholder if only one thread will be
 * required.
 */
interface ExecutorServiceFactory {
  /** Returns an executor service that uses [Executors.newSingleThreadExecutor]. */
  fun single(nameFormat: String): ExecutorService

  /** Returns an executor service that uses [Executors.newFixedThreadPool]. */
  fun fixed(nameFormat: String, threadCount: Int): ExecutorService

  /** Returns an executor service that uses [Executors.newCachedThreadPool]. */
  fun unbounded(nameFormat: String): ExecutorService

  /** Returns an executor service that uses [Executors.newScheduledThreadPool]. */
  fun scheduled(nameFormat: String, threadCount: Int): ScheduledExecutorService

  /** Explicitly stop all threads. This should only be used for tests. */
  fun stop()
}
