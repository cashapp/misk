package misk.cloud.gcp.tracing

import com.google.cloud.logging.LogEntry
import com.google.cloud.logging.Payload
import datadog.opentracing.DDTracer
import datadog.trace.common.writer.Writer
import datadog.trace.core.DDSpan
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
      .build()
    tracer.traceWithSpan("test span") {
      val logEntryBuilder = LogEntry.newBuilder(Payload.StringPayload.of("payload"))

      TracingLoggingEnhancer().enhanceLogEntry(tracer, logEntryBuilder)

      val logEntry = logEntryBuilder.build()
      assertThat(logEntry.labels).isEqualTo(
        mapOf(
          "appengine.googleapis.com/trace_id" to
            tracer.activeSpan().context().toTraceId()
        )
      )
    }
  }

  @Test fun noopTracer() {
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

  override fun flush(): Boolean {
    return true
  }

  override fun incrementDropCounts(p0: Int) {
  }

}
