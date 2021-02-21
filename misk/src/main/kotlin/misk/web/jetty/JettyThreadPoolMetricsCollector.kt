package misk.web.jetty

import com.google.common.util.concurrent.AbstractScheduledService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class JettyThreadPoolMetricsCollector @Inject internal constructor(
  private val metrics: ThreadPoolMetrics
) : AbstractScheduledService() {
  override fun scheduler(): Scheduler =
    Scheduler.newFixedDelaySchedule(REFRESH_RATE_MS, REFRESH_RATE_MS, TimeUnit.MILLISECONDS)

  override fun runOneIteration() {
    metrics.refresh()
  }

  companion object {
    const val REFRESH_RATE_MS = 500L
  }
}
