package wisp.tracing

import io.opentracing.Span
import io.opentracing.mock.MockSpan
import io.opentracing.tag.Tags
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.tracing.testing.ConcurrentMockTracer
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class TracerExtTest {

    private val tracer = ConcurrentMockTracer()

    @Test
    fun traceTracedMethod() {
        assertThat(tracer.finishedSpans().size).isEqualTo(0)
        val spanUsed = tracer.traceWithSpan("traceMe") { span -> span }
        val span = tracer.take()
        assertThat(spanUsed).isEqualTo(span)
        assertThat(span.tags()).isEmpty()
    }

    @Test
    fun traceTracedMethodWithTags() {
        val tags = mapOf("a" to "b", "x" to "y")

        assertThat(tracer.finishedSpans().size).isEqualTo(0)
        val spanUsed = tracer.traceWithSpan("traceMe", tags) { span -> span }
        val span = tracer.take()
        assertThat(spanUsed).isEqualTo(span)
        assertThat(span.tags()).isEqualTo(tags)
    }

    @Test
    fun tagTracingFailures() {
        assertThat(tracer.finishedSpans().size).isEqualTo(0)
        assertFailsWith<Exception> {
            tracer.trace("failedTrace") {
                throw Exception("sadness")
            }
        }
        val span = tracer.take()
        assertThat(span.tags()[Tags.ERROR.key]).isEqualTo(true)
    }

    @Test
    fun nestedTracing() {
        assertThat(tracer.finishedSpans().size).isEqualTo(0)
        val (parentSpan, childSpan) = tracer.traceWithSpan("parent") { span1 ->
            span1 to tracer.traceWithSpan("child") { span2 -> span2 }
        }
        val span0 = tracer.take()
        val span1 = tracer.take()
        assertThat(span0).isEqualTo(childSpan)
        assertThat(span1).isEqualTo(parentSpan)

        val parentContext = parentSpan.context() as MockSpan.MockContext
        val childContext = childSpan.context() as MockSpan.MockContext
        assertThat(childContext.traceId()).isEqualTo(parentContext.traceId())
        assertThat((childSpan as MockSpan).parentId()).isEqualTo(parentContext.spanId())
    }

    @Test
    fun nonNestedTracing() {
        assertThat(tracer.finishedSpans().size).isEqualTo(0)
        val (parentSpan, childSpan) = tracer.traceWithSpan("parent") { span1 ->
            span1 to tracer.traceWithNewRootSpan("child") { span2 -> span2 }
        }
        val span0 = tracer.take()
        val span1 = tracer.take()
        assertThat(span0).isEqualTo(childSpan)
        assertThat(span1).isEqualTo(parentSpan)

        val parentContext = parentSpan.context() as MockSpan.MockContext
        val childContext = childSpan.context() as MockSpan.MockContext
        assertThat(childContext.traceId()).isNotEqualTo(parentContext.traceId())
        assertThat((childSpan as MockSpan).parentId()).isNotEqualTo(parentContext.spanId())
    }

    @Test
    fun `traceWithNewRootSpan can retain previous baggage`() {
        tracer.traceWithSpan("parent") { span ->
            val baggage = mapOf("parent-baggage" to "blah blah blah")
            span.setBaggageItems(baggage)
            val parentBaggage = span.context().baggageItems()

            tracer.traceWithNewRootSpan("new-root", retainBaggage = true) { span ->
                val newRootBaggage = span.context().baggageItems()
                assertContainsAll(newRootBaggage, parentBaggage)
                assertContainsAll(newRootBaggage, baggage.asIterable())
            }

            tracer.traceWithNewRootSpan("new-root-no-baggage", retainBaggage = false) { span ->
                val noBaggage = span.context().baggageItems().toList()
                assertTrue(
                        noBaggage.isEmpty(),
                        "Expected no baggage on new span ignoring parent without baggage retention"
                )
            }
        }
    }

    @Test
    fun `can create and use new scopes in a single span`() {
        val spanRef = AtomicReference<Span>()
        tracer.traceWithSpan("test-scoped") { span ->
            spanRef.set(span)
            tracer.withNewScope(span) {
                assertNotFinished(span)
            }
        }
        assertFinished(spanRef.get())

        val spans = tracer.finishedSpans()
        assertTrue(spans.size == 1, "Expected exactly one span")
        assertContains(spans.map { it.operationName() }, "test-scoped")
    }
}
