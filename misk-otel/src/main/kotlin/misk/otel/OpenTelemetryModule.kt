package misk.otel

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import misk.inject.KInstallOnceModule

class OpenTelemetryModule : KInstallOnceModule() {
  override fun configure() {
    bind<OpenTelemetry>().toInstance(GlobalOpenTelemetry.get())
    bind<Meter>().toProvider { GlobalOpenTelemetry.getMeter("misk") }
    bind<Tracer>().toProvider { GlobalOpenTelemetry.getTracer("misk") }
  }
}
