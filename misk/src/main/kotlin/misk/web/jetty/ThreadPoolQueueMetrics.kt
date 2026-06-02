package misk.web.jetty

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import misk.metrics.Metrics

@Singleton
class ThreadPoolQueueMetrics @Inject internal constructor(metrics: Metrics) {
  private val queueLatencyHistogram =
    metrics.histogram("jetty_thread_pool_queue_latency", "time spent by items in the queue", listOf("latency"))

  fun recordQueueLatency(latency: Duration) {
    queueLatencyHistogram.record(latency.toMillis().toDouble(), "latency")
  }
}
