package misk.web.jetty

import misk.metrics.v2.Metrics
import org.eclipse.jetty.http2.parser.RateControl
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.util.NanoTime
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Misk's RateControl implementation with observability for monitoring HTTP/2 frame rate limiting.
 * Almost the same implementation as [org.eclipse.jetty.http2.parser.WindowRateControl].
 */
internal class MeasuredWindowRateControl internal constructor(
  private val metrics: Metrics,
  private val maxEvents: Int,
) : RateControl {

  private val gauge = metrics.peakGauge(
    "jetty_http2_rate_control_events_peak",
    "Peak gauge of observed events per second"
  )
  private val counter = metrics.counter(
    "jetty_http2_rate_control_events_limited",
    "Count of rate limited events"
  )

  private val events = ConcurrentLinkedQueue<Long>()
  private val size = AtomicInteger()
  private val window = Duration.ofSeconds(1).toNanos()

  override fun onEvent(event: Any?): Boolean {
    val now = NanoTime.now()
    while (true) {
      val time = events.peek() ?: break
      if (NanoTime.isBefore(now, time)) break
      if (events.remove(time)) {
        size.decrementAndGet()
      }
    }
    events.add(now + window)

    val count = size.incrementAndGet()
    gauge.record(count.toDouble())

    if (maxEvents == -1) return true

    val allowed = count <= maxEvents
    if (!allowed) counter.inc()
    return allowed
  }

  class Factory constructor(
    private val metrics: Metrics,
    private val maxEventRate: Int
  ) : RateControl.Factory {
    override fun newRateControl(endPoint: EndPoint): RateControl {
      return MeasuredWindowRateControl(metrics, maxEventRate)
    }
  }
}