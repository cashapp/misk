package misk.tracing.interceptors

import com.google.inject.util.Modules
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.tracing.Trace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class TracingMethodInterceptorTest {
  @MiskTestModule
  val module = Modules.combine(
      TracingMethodInterceptorModule(),
      MockTracingBackendModule()
  )

  @Inject private lateinit var tracer: Tracer

  @Inject private lateinit var testObject: TestObject

  @Test
  fun traceAnnotationTraces() {
    val mockTracer = tracer as MockTracer

    assertThat(mockTracer.finishedSpans().size).isEqualTo(0)
    testObject.testTraceMethod()
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
  }
}

open class TestObject {
  @Trace
  open fun testTraceMethod() {
    print("Trace me!")
  }
}
