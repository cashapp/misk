package misk.web.jetty

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics
import org.eclipse.jetty.http2.parser.RateControl
import org.eclipse.jetty.io.EndPoint

/**
 * Misk's RateControl implementation with observability for monitoring HTTP/2frame rate limiting.
 * Almost the same implementation as {@code org.eclipse.jetty.http2.parser.WindowRateControl}.
  */
@Singleton internal class MeasuredWindowRateControl @Inject internal constructor(
  metrics: Metrics,
) : RateControl {

  override fun onEvent(event: Any?): Boolean {
    TODO("Not yet implemented")
  }

  inner class Factor constructor(
    maxEventRate
  ) : RateControl.Factory {
    override fun newRateControl(endPoint: EndPoint): RateControl {
      TODO("Not yet implemented")
    }
  }
}