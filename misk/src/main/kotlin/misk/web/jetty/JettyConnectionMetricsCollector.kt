package misk.web.jetty

import com.google.common.util.concurrent.AbstractScheduledService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class JettyConnectionMetricsCollector @Inject internal constructor(
  private val metrics: ConnectionMetrics
) : AbstractScheduledService() {
  private val listeners = CopyOnWriteArrayList<ConnectionListener>()

  fun newConnectionListener(protocol: String, port: Int): ConnectionListener {
    val listener = ConnectionListener(protocol, port, metrics)
    listeners.add(listener)
    return listener
  }

  fun refreshMetrics() {
    listeners.forEach { it.refreshMetrics() }
  }

  override fun scheduler(): Scheduler =
    Scheduler.newFixedRateSchedule(REFRESH_RATE_MS, REFRESH_RATE_MS, TimeUnit.MILLISECONDS)

  override fun runOneIteration() {
    refreshMetrics()
  }

  companion object {
    const val REFRESH_RATE_MS = 500L
  }
}
