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

  /**
   * Awaits a span named [operationName], removes it, and returns it. Spans with other names are
   * consumed and discarded.
   */
  fun take(operationName: String): MockSpan {
    while (true) {
      val span = take()
      if (span.operationName() == operationName) return span
    }
  }

  override fun onSpanFinished(mockSpan: MockSpan) {
    super.onSpanFinished(mockSpan)
    queue.put(mockSpan)
  }
}
