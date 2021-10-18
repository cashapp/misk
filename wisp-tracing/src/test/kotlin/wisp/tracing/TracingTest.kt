package wisp.tracing

import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import org.junit.jupiter.api.assertThrows
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TracingTest {
  private lateinit var tracer: MockTracer

  @BeforeTest fun `set up`() {
    tracer = MockTracer()
  }

  @Test fun `Tracer#scoped() produces a trace`() {
    val span = tracer.buildSpan("test-scoped").start()
    tracer.scoped(span) {
      // NOP.
    }
    assertFinished(span)

    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
    assertContains(spans.map { it.operationName() }, "test-scoped")
  }

  @Test fun `Tracer#newScope() allows spans to be re-used before they are closed`() {
    val span = tracer.buildSpan("test-scoped").start()
    tracer.scoped(span) { outerScope ->
      tracer.newScope(span) { innerScope ->
        assertNotSame(outerScope, innerScope, "Expected a new scope")
      }
      assertNotFinished(span)
    }
    assertFinished(span)
    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
  }

  @Test fun `Tracer#scoped() can be nested with new spans`() {
    val span = tracer.buildSpan("parent-span").start()
    tracer.scoped(span) {
      val childSpan = tracer.buildSpan("child-span").asChildOf(span).start()
      tracer.scoped(childSpan) {
        // NOP.
      }
      assertFinished(childSpan)
    }
    assertFinished(span)
    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 2, "Expected exactly two spans")
  }

  @Test fun `Span#setBaggageItems() works`() {
    // No baggage.
    val noBaggage = tracer.buildSpan("no-baggage").start()
    noBaggage.setBaggageItems(mapOf())
    tracer.scoped(noBaggage) {
      // NOP.
    }
    var spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")

    assertTrue(
      spans.map { it.context().baggageItems() }.first().toList().isEmpty(),
      "Expected no baggage"
    )

    tracer.reset()

    // With baggage.
    val withBaggage = tracer.buildSpan("set-baggage").start()
    withBaggage.setBaggageItems(
      mapOf(
        "movie" to "star wars",
        "release-year" to 1977,
        "producer" to Person("George Lucas")
      )
    )

    tracer.scoped(withBaggage) {
      // NOP.
    }

    spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")

    assertContainsAll(
      spans.map { it.context().baggageItems() }.first(),
      mapOf(
        "movie" to "star wars",
        "release-year" to "1977",
        "producer" to Person("George Lucas").toString()
      ).entries
    )
  }

  private data class Person(val name: String)

  private fun assertFinished(span: MockSpan) {
    assertTrue(span.finishMicros() > 0, "Expected span to be finished.")
  }

  private fun assertNotFinished(span: MockSpan) {
    assertThrows<AssertionError> { span.finishMicros() }
  }

  private fun <T> assertContainsAll(iterable: Iterable<T>, iterable2: Iterable<T>) {
    for (item in iterable2) {
      assertContains(iterable, item, "Expected $iterable to contain $item")
    }
  }
}
