package misk.testing

import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extends [MockTracer] for use in concurrent environments, such as a web server and test client.
 * Prefer this wherever you'd otherwise use [MockTracer].
 */
@Singleton
class ConcurrentMockTracer @Inject constructor() : MockTracer() {
  private val queue = LinkedBlockingDeque<MockSpan>()

  /** Awaits a span, removes it, and returns it. */
  fun take(): MockSpan {
    return queue.poll(500, TimeUnit.MILLISECONDS) ?: throw IllegalArgumentException("no spans!")
  }

  override fun onSpanFinished(mockSpan: MockSpan) {
    super.onSpanFinished(mockSpan)
    queue.put(mockSpan)
  }
}
