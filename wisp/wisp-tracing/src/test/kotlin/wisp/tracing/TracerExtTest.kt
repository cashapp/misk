package wisp.tracing

import io.opentracing.mock.MockSpan
import io.opentracing.tag.Tags
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.tracing.testing.ConcurrentMockTracer
import kotlin.test.assertFailsWith

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
}
