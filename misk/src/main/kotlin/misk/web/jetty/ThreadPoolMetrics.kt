package misk.web.jetty

import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ThreadPoolMetrics @Inject internal constructor(
  metrics: Metrics,
  private val threadPool: MeasuredThreadPool
) {
  val utilization = metrics.gauge(
    "jetty_thread_pool_utilization",
    "current utilization of the jetty thread pool compared to the current pool size"
  )

  val utilizationMax = metrics.gauge(
    "jetty_thread_pool_utilization_max",
    "current utilization of the jetty thread pool compared to the max pool size"
  )

  val busyThreads = metrics.gauge(
    "jetty_thread_pool_busy",
    "current number of busy threads"
  )

  val size = metrics.gauge(
    "jetty_thread_pool_size",
    "current number of threads in the jetty thread pool"
  )

  val queuedJobs = metrics.gauge(
    "jetty_thread_pool_queued_jobs",
    "number of jobs queued in the jetty thread pool"
  )

  fun refresh() {
    utilization.set(ratio(threadPool.activeCount().toDouble(), threadPool.poolSize().toDouble()))
    utilizationMax.set(
      ratio(threadPool.activeCount().toDouble(), threadPool.maxPoolSize().toDouble())
    )
    size.set(threadPool.poolSize().toDouble())
    busyThreads.set(threadPool.activeCount().toDouble())
    queuedJobs.set(threadPool.queueSize().toDouble())
  }

  private fun ratio(numerator: Double, denominator: Double) =
    if (denominator.isNaN() || denominator.isInfinite() || denominator == 0.0) Double.NaN
    else numerator / denominator
}
