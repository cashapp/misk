package misk.tracing

import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.assertj.core.api.Assertions.assertThat
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
    miskTracer.trace("traceMe", ::traceMe)
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
  }

  fun traceMe(){}
}