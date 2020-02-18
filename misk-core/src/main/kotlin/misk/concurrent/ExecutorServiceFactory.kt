package misk.concurrent

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

/**
 * Inject this rather than using the [Executors] factory class to create thread pools. Executors
 * created with this factory will automatically be shut down when the service or test completes.
 */
interface ExecutorServiceFactory {
  /** Returns a new [ExecutorServiceFactory] that uses [threadFactory] to create threads. */
  fun withThreadFactory(threadFactory: ThreadFactory): ExecutorServiceFactory

  /**
   * Returns a new [ExecutorServiceFactory] that uses [nameFormat] to name threads.
   *
   * @param nameFormat a string in the format specified by [ThreadFactoryBuilder.setNameFormat],
   *     like "rpc-pool-%d". If the string has a single `%d` placeholder it will be assigned
   *     sequentially. Omit the placeholder if only one thread will be required.
   */
  fun named(nameFormat: String): ExecutorServiceFactory

  /** Returns an executor service that uses [Executors.newSingleThreadExecutor]. */
  fun single(): ExecutorService

  /** Returns an executor service that uses [Executors.newFixedThreadPool]. */
  fun fixed(threadCount: Int): ExecutorService

  /** Returns an executor service that uses [Executors.newCachedThreadPool]. */
  fun unbounded(): ExecutorService

  /** Returns an executor service that uses [Executors.newScheduledThreadPool]. */
  fun scheduled(threadCount: Int): ScheduledExecutorService
}
