package wisp.tracing

import io.opentracing.Span
import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TracingTest {
  private lateinit var tracer: MockTracer

  @BeforeTest fun `set up`() {
    tracer = MockTracer()
  }

  @Test fun `Tracer#spanned() produces a trace and finishes the span`() {
    // NB: The span isn't finished until after block executes, but is intentionally unavailable
    // afterwards to avoid undefined behaviour from re-using finished spans.
    // To assert that the span has finished, we take an AtomicReference.
    val spanRef = AtomicReference<Span>()
    tracer.spanned("test-span") {
      spanRef.set(span)
      assertNotFinished(span)
    }
    assertFinished(spanRef.get())

    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
    assertContains(spans.map { it.operationName() }, "test-span")
  }

  @Test fun `Tracer#scoped() produces a trace`() {
    val span = tracer.buildSpan("test-scoped").start()
    tracer.scoped(span, finishSpan = true) {
      assertNotFinished(span)
    }
    assertFinished(span)

    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
    assertContains(spans.map { it.operationName() }, "test-scoped")
  }

  @Test fun `Tracer#scoped() allows spans to be re-used before they are closed`() {
    val span = tracer.buildSpan("test-scoped").start()
    tracer.scoped(span, finishSpan = true) { outerScope ->
      tracer.scoped(span, finishSpan = false /* default */) { innerScope ->
        assertNotSame(outerScope, innerScope, "Expected a new scope")
      }
      assertNotFinished(span)
    }
    assertFinished(span)
    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
  }

  @Test fun `Tracer#spanned() can be used with child spans`() {
    val parentRef = AtomicReference<Span>()
    tracer.spanned("parent-span") {
      parentRef.set(span)
      assertSame(tracer.activeSpan(), span)

      val childSpan = tracer.childSpan("child-span", span)
      tracer.scoped(childSpan, finishSpan = true) {
        assertSame(tracer.activeSpan(), childSpan)
        assertNotFinished(childSpan)
      }

      assertSame(tracer.activeSpan(), span)
      assertFinished(childSpan)
      assertNotFinished(span)
    }
    assertFinished(parentRef.get())
    val spans = tracer.finishedSpans()
    assertTrue(spans.size == 2, "Expected exactly two spans")
  }

  @Test fun `Tracer#spanned() can ignore active spans`() {
    tracer.spanned("parent") {
      val parentId = span.context().toTraceId()

      tracer.spanned("child") {
        val childId = this.span.context().toTraceId()
        assertEquals(parentId, childId)
      }

      tracer.spanned("child-ignoring-span", ignoreActiveSpan = true) {
        val childId = this.span.context().toTraceId()
        assertNotEquals(parentId, childId)
      }
    }
  }

  @Test fun `Tracer#spanned() can retain previous baggage`() {
    tracer.spanned("parent") {
      val baggage = mapOf("parent-baggage" to "blah blah blah")
      span.setBaggageItems(baggage)
      val parentBaggage = span.context().baggageItems()

      tracer.spanned("child", ignoreActiveSpan = true, retainBaggage = true) {
        val childBaggage = this.span.context().baggageItems()
        assertContainsAll(childBaggage, parentBaggage)
        assertContainsAll(childBaggage, baggage.asIterable())
      }

      tracer.spanned("child-ignoring-span", ignoreActiveSpan = true, retainBaggage = false) {
        val childBaggage = this.span.context().baggageItems().toList()
        assertTrue(
          childBaggage.isEmpty(),
          "Expected no baggage on child span ignoring parent without baggage retention"
        )
      }
    }
  }

  @Test fun `Span#setBaggageItems() works`() {
    // No baggage.
    tracer.spanned("no-baggage") {
      span.setBaggageItems(mapOf())
    }
    var spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
    assertTrue(
      spans.map { it.context().baggageItems() }.first().toList().isEmpty(),
      "Expected no baggage"
    )

    tracer.reset()

    // With baggage.
    tracer.spanned("set-baggage") {
      span.setBaggageItems(
        mapOf(
          "movie" to "star wars",
          "release-year" to 1977,
          "producer" to Person("George Lucas")
        )
      )
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

  @Test fun `Span#setTags() works`() {
    // No tags.
    tracer.spanned("no-tags") {
      span.setTags(listOf())
    }
    var spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")
    assertTrue(
      spans.map { it.tags() }.first().toList().isEmpty(),
      "Expected no tags"
    )
    tracer.reset()

    // With tags.
    tracer.spanned("set-tags") {
      span.setTags(
        listOf(
          Tag("int", 9999),
          Tag("long", Long.MAX_VALUE),
          Tag("double", Double.MAX_VALUE),
          Tag("float", Float.MIN_VALUE),
          Tag("string", "string"),
          Tag("boolean", true),
        )
      )
    }
    spans = tracer.finishedSpans()
    assertTrue(spans.size == 1, "Expected exactly one span")

    assertContainsAll(
      spans.map { it.tags() }.first().entries,
      mapOf(
        "int" to 9999,
        "long" to Long.MAX_VALUE,
        "double" to Double.MAX_VALUE,
        "float" to Float.MIN_VALUE,
        "string" to "string",
        "boolean" to true
      ).entries
    )
  }

  private data class Person(val name: String)

  private fun assertFinished(span: Span) {
    span as? MockSpan ?: throw AssertionError("Expected MockSpan")
    assertTrue(span.finishMicros() > 0, "Expected span to be finished.")
  }

  private fun assertNotFinished(span: Span) {
    span as? MockSpan ?: throw AssertionError("Expected MockSpan")
    assertFailsWith<AssertionError> { span.finishMicros() }
  }

  private fun <T> assertContainsAll(iterable: Iterable<T>, iterable2: Iterable<T>) {
    for (item in iterable2) {
      assertContains(iterable, item, "Expected $iterable to contain $item")
    }
  }
}
