package misk.cloud.gcp.tracing

import brave.Tracing
import brave.opentracing.BraveTracer
import com.google.cloud.logging.LogEntry
import com.google.cloud.logging.Payload
import com.uber.jaeger.Span
import com.uber.jaeger.Tracer
import com.uber.jaeger.reporters.NoopReporter
import com.uber.jaeger.samplers.ConstSampler
import io.opentracing.noop.NoopTracerFactory
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class TracingLoggingEnhancerTest {
  @Test fun enhanceJaegerTracer() {
    val tracer = Tracer.Builder("jaegerbombs", NoopReporter(), ConstSampler(true)).build()
    val scope = tracer.buildSpan("test span").startActive(true)
    scope.use {
      val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))

      TracingLoggingEnhancer().enhanceLogEntry(tracer, logEntryBuilder)

      val logEntry = logEntryBuilder.build()
      assertThat(logEntry.labels).isEqualTo(mapOf(
          "appengine.googleapis.com/trace_id" to
              (tracer.activeSpan() as Span).context().traceId.toString()))
    }
  }

  @Test fun enhanceBraveTracer() {
    val tracer = BraveTracer.newBuilder(Tracing.newBuilder().build()).build()
    val scope = tracer.buildSpan("test span").startActive(true)
    scope.use {
      val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))

      TracingLoggingEnhancer().enhanceLogEntry(tracer, logEntryBuilder)

      val logEntry = logEntryBuilder.build()
      assertThat(logEntry.labels).isEqualTo(mapOf(
          "appengine.googleapis.com/trace_id" to
              "0000000000000000" + tracer.activeSpan().unwrap().context().traceIdString()))
    }
  }

  @Test fun ignoreEnhancement() {
    val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))
    TracingLoggingEnhancer()
        .enhanceLogEntry(NoopTracerFactory.create(), logEntryBuilder)
    val logEntry = logEntryBuilder.build()

    assertThat(logEntry.labels).isEmpty()
  }
}