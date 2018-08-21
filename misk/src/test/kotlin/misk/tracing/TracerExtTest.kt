package misk.tracing

import io.opentracing.Tracer
import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class TracerExtTest {
  @MiskTestModule
  val module = MockTracingBackendModule()

  @Inject private lateinit var tracer: Tracer

  @Test
  fun traceTracedMethod() {
    val mockTracer = tracer as MockTracer

    assertThat(mockTracer.finishedSpans().size).isEqualTo(0)
    val spanUsed = tracer.traceWithSpan("traceMe") { span -> span }
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
    assertThat(spanUsed).isEqualTo(mockTracer.finishedSpans().first())
  }

  @Test
  fun tagTracingFailures() {
    val mockTracer = tracer as MockTracer

    assertThat(mockTracer.finishedSpans().size).isEqualTo(0)
    assertFailsWith<ActionException> {
      tracer.trace("failedTrace") {
        throw ActionException(StatusCode.BAD_REQUEST, "sadness")
      }
    }
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
    assertThat(mockTracer.finishedSpans().get(0).tags().get(Tags.ERROR.key)).isEqualTo(true)
  }

  @Test
  fun nestedTracing() {
    val mockTracer = tracer as MockTracer

    assertThat(mockTracer.finishedSpans().size).isEqualTo(0)
    val (parentSpan, childSpan) = tracer.traceWithSpan("parent") { span1 ->
      span1 to tracer.traceWithSpan("child") { span2 -> span2 }
    }
    assertThat(mockTracer.finishedSpans().size).isEqualTo(2)
    assertThat(mockTracer.finishedSpans()[0]).isEqualTo(childSpan)
    assertThat(mockTracer.finishedSpans()[1]).isEqualTo(parentSpan)

    val parentContext = parentSpan.context() as MockSpan.MockContext
    val childContext = childSpan.context() as MockSpan.MockContext
    assertThat(childContext.traceId()).isEqualTo(parentContext.traceId())
    assertThat((childSpan as MockSpan).parentId()).isEqualTo(parentContext.spanId())
  }
}
