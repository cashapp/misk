//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//
package misk.web.jetty

import io.prometheus.client.Counter
import misk.metrics.v2.Metrics
import misk.metrics.v2.PeakGauge
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
class MeasuredWindowRateControl private constructor(
  private val maxEvents: Int,
  private val rateEventsPeakGauge: PeakGauge,
  private val rateLimitedEventCounter: Counter,
) : RateControl {


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
    rateEventsPeakGauge.record(count.toDouble())
    if (maxEvents == -1) return true

    val allowed = count <= maxEvents
    if (!allowed) rateLimitedEventCounter.inc()
    return allowed
  }

  class Factory(
    metrics: Metrics,
    private val maxEventRate: Int
  ) : RateControl.Factory {

    // Metrics are instantiated in the factory and passed to the class because
    // there overhead in recreating them for every http/2 connection
    // (can result in java.io.IOException: FRAME_SIZE_ERROR: 4740180 on the client)
    private val rateEventsPeakGauge = metrics.peakGauge(
      "jetty_http2_rate_control_events_peak",
      "Peak gauge of observed events per second"
    )
    private val rateLimitedEventCounter = metrics.counter(
      "jetty_http2_rate_control_events_limited",
      "Count of rate limited events"
    )

    override fun newRateControl(endPoint: EndPoint): RateControl {
      return MeasuredWindowRateControl(
        maxEventRate,
        rateEventsPeakGauge,
        rateLimitedEventCounter)
    }
  }
}
