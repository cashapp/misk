package misk.web.jetty

import misk.metrics.Metrics
import java.time.Duration
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class ThreadPoolQueueMetrics @Inject internal constructor(
  metrics: Metrics
) {
  private val queueLatencyHistogram = metrics.histogram(
    "jetty_thread_pool_queue_latency",
    "time spent by items in the queue",
    listOf("latency")
  )

  fun recordQueueLatency(latency: Duration) {
    queueLatencyHistogram.record(latency.toMillis().toDouble(), "latency")
  }
}
