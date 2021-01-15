package misk.eventrouter

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * An executor service that holds enqueued work until explicitly executed. Useful for making tests
 * deterministic.
 */
internal class QueueingExecutorService : AbstractExecutorService() {
  private val queue = LinkedBlockingQueue<Runnable>()
  private var processing = false

  /** Returns the number of runnables that were run. */
  fun processEverything(): Int {
    check(!processing) { "already processing: recursive call?" }

    processing = true
    try {
      var result = 0
      while (true) {
        val runnable = queue.poll() ?: return result
        result++
        runnable.run()
      }
    } finally {
      processing = false
    }
  }

  /** Returns true if this processor is currently processing. */
  fun isProcessing() = processing

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
