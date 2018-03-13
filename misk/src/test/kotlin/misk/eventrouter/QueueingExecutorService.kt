package misk.eventrouter

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * An executor service that holds enqueued work until explicitly executed. Useful for making tests
 * deterministic.
 */
@Singleton
class QueueingExecutorService : AbstractExecutorService() {
  private val queue = LinkedBlockingQueue<Runnable>()

  /** Returns the number of runnables that were run. */
  fun processEverything(): Int {
    var result = 0
    while (true) {
      val runnable = queue.poll() ?: return result
      result++
      runnable.run()
    }
  }

  override fun isTerminated(): Boolean = false

  override fun execute(command: Runnable) {
    queue.add(command)
  }

  override fun shutdown() = Unit

  override fun shutdownNow(): MutableList<Runnable> = queue.toMutableList()

  override fun isShutdown(): Boolean = false

  override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
    throw UnsupportedOperationException()
  }
}