package misk.tracing

import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import misk.exceptions.ActionException
import misk.exceptions.BadRequestException
import misk.exceptions.StatusCode
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class MiskTracerTest {
  @MiskTestModule
  val module = MockTracingBackendModule()

  @Inject private lateinit var tracer: Tracer
  @Inject private lateinit var miskTracer: MiskTracer

  @Test
  fun traceTracedMethod() {
    val mockTracer = tracer as MockTracer

    assertThat(mockTracer.finishedSpans().size).isEqualTo(0)
    val spanUsed = miskTracer.trace("traceMe", ::traceMe)
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
    assertThat(spanUsed).isEqualTo(mockTracer.finishedSpans().first())
  }

  @Test
  fun tagTracingFailures() {
    val mockTracer = tracer as MockTracer

    assertThat(mockTracer.finishedSpans().size).isEqualTo(0)
    assertThrows(ActionException::class.java, { miskTracer.trace("failedTrace", ::failedTrace) })
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
    assertThat(mockTracer.finishedSpans().get(0).tags().get(Tags.ERROR.key)).isEqualTo(true)
  }

  fun traceMe(span: Span) : Span {
    return span
  }

  fun failedTrace(span: Span) {
    throw ActionException(StatusCode.BAD_REQUEST, "sadness")
  }
}