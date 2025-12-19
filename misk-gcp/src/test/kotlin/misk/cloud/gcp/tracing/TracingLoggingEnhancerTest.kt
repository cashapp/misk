package misk.cloud.gcp.tracing

import com.google.cloud.logging.LogEntry
import com.google.cloud.logging.Payload
import io.opentracing.mock.MockTracer
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.util.GlobalTracer
import misk.testing.MiskTest
import misk.tracing.traceWithSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MiskTest
class TracingLoggingEnhancerTest {

  private val tracer = MockTracer()

  @BeforeAll
  fun initClass() {
    GlobalTracer.registerIfAbsent(tracer)
  }

  @BeforeEach
  fun setup() {
    tracer.reset()
  }

  @Test
  fun enhanceDatadogTracer() {
    val tracer = GlobalTracer.get()
    tracer.traceWithSpan("test span") {
      val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))

      TracingLoggingEnhancer().enhanceLogEntry(tracer, logEntryBuilder)

      val logEntry = logEntryBuilder.build()
      assertThat(logEntry.labels)
        .isEqualTo(mapOf("appengine.googleapis.com/trace_id" to tracer.activeSpan().context().toTraceId()))
    }
  }

  @Test
  fun noopTracer() {
    val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))
    TracingLoggingEnhancer().enhanceLogEntry(NoopTracerFactory.create(), logEntryBuilder)
    val logEntry = logEntryBuilder.build()

    assertThat(logEntry.labels).isEmpty()
  }
}
