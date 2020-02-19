package misk.cloud.gcp.tracing

import brave.Tracing
import brave.opentracing.BraveTracer
import com.google.cloud.logging.LogEntry
import com.google.cloud.logging.Payload
import io.jaegertracing.internal.JaegerTracer
import io.jaegertracing.internal.reporters.NoopReporter
import io.jaegertracing.internal.samplers.ConstSampler
import io.opentracing.Span
import io.opentracing.noop.NoopTracerFactory
import misk.testing.MiskTest
import misk.tracing.traceWithSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class TracingLoggingEnhancerTest {
  @Test fun enhanceJaegerTracer() {
    val tracer = JaegerTracer.Builder("jaegerbombs")
        .withReporter(NoopReporter())
        .withSampler(ConstSampler(true))
        .build()

    tracer.traceWithSpan("test span") {
      val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))

      TracingLoggingEnhancer().enhanceLogEntry(tracer, logEntryBuilder)

      val logEntry = logEntryBuilder.build()
      assertThat(logEntry.labels).isEqualTo(mapOf(
          "appengine.googleapis.com/trace_id" to
              (tracer.activeSpan() as Span).context().toTraceId()))
    }
  }

  @Test fun enhanceBraveTracer() {
    val tracer = BraveTracer.newBuilder(Tracing.newBuilder().build()).build()
    tracer.traceWithSpan("test span") {
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
