package misk.cloud.gcp.tracing

import brave.Tracing
import brave.opentracing.BraveTracer
import com.google.cloud.logging.LogEntry
import com.google.cloud.logging.Payload
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.common.writer.Writer
import io.opentracing.Span
import io.opentracing.noop.NoopTracerFactory
import misk.testing.MiskTest
import misk.tracing.traceWithSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class TracingLoggingEnhancerTest {
  @Test fun enhanceDatadogTracer() {
    val tracer = DDTracer.builder()
            .writer(NoopWriter())
            .build();
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

class NoopWriter : Writer {
  override fun start() {
  }

  override fun write(trace: MutableList<DDSpan>?) {
  }

  override fun close() {
  }

  override fun incrementTraceCount() {
  }
}
