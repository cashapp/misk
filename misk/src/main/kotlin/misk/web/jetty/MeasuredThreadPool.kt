package misk.web.jetty

import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.util.concurrent.ThreadPoolExecutor

/**
 * A common interface that can emit metrics about a thread pool.
 */
interface MeasuredThreadPool {

  /**
   * The current size of the thread pool.
   */
  fun poolSize(): Int

  /**
   * The number of active threads.
   */
  fun activeCount(): Int

  /**
   * The maximum size the pool can grow to.
   */
  fun maxPoolSize(): Int

  /**
   * The current number of tasks in the queue waiting to be processed by the thread pool.
   */
  fun queueSize(): Int
}

/**
 * A [MeasuredThreadPool] for a [QueuedThreadPool]
 */
class MeasuredQueuedThreadPool(private val threadPool: QueuedThreadPool) : MeasuredThreadPool {
  override fun poolSize(): Int = threadPool.threads

  override fun activeCount(): Int = threadPool.busyThreads

  override fun maxPoolSize(): Int = threadPool.maxThreads

  override fun queueSize(): Int = threadPool.queueSize
}

/**
 * A [MeasuredThreadPool] for a [ThreadPoolExecutor]
 */
class MeasuredThreadPoolExecutor(private val threadPool: ThreadPoolExecutor) : MeasuredThreadPool {
  override fun poolSize(): Int = threadPool.poolSize

  override fun activeCount(): Int = threadPool.activeCount

  override fun maxPoolSize(): Int = threadPool.maximumPoolSize

  override fun queueSize(): Int = threadPool.queue.size
}
