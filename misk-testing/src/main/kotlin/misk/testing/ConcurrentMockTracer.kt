package misk.testing

import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import jakarta.inject.Inject
import jakarta.inject.Singleton
import wisp.logging.getLogger

/**
 * Extends [MockTracer] for use in concurrent environments, such as a web server and test client.
 * Prefer this wherever you'd otherwise use [MockTracer].
 */
// TODO(keefer): Deprecate this (or forward the type via delegate) when wisp-tracing-test-fixtures
//  publication is fixed. wisp-tracing provides a preferred copy of this class.
@Singleton
class ConcurrentMockTracer @Inject constructor() : MockTracer() {
  private val queue = LinkedBlockingDeque<MockSpan>()

  /** Awaits a span, removes it, and returns it. */
  fun take(): MockSpan {
    return queue.poll(1000, TimeUnit.MILLISECONDS) ?: throw IllegalArgumentException("no spans!")
  }

  /**
   * Awaits a span named [operationName], removes it, and returns it. Spans with other names are
   * consumed and discarded.
   */
  fun take(operationName: String): MockSpan {
    while (true) {
      val span = take()
      if (span.operationName() == operationName) return span
      else log.info("Span dropped: ${span.operationName()} \n" )
    }
  }

  override fun onSpanFinished(mockSpan: MockSpan) {
    super.onSpanFinished(mockSpan)
    queue.put(mockSpan)
  }

  companion object {
    private val log = getLogger<ConcurrentMockTracer>()
  }
}
