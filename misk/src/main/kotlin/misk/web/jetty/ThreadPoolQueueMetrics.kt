package misk.web.jetty

import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadPoolQueueMetrics @Inject internal constructor(
  metrics: Metrics
) {
  private val queueLatencyHistogram = metrics.histogram(
      "jetty_thread_pool_queue_latency",
      "time spent by items in the queue",
      listOf("latency")
  )

  fun recordQueueLatency(latency: Long) {
    queueLatencyHistogram.record(latency.toDouble(), "latency")
  }
}