package misk.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import misk.inject.KAbstractModule

/**
 * Binds an application-owned [OpenTelemetry] instance for dependency injection.
 *
 * Real processes bind the SDK returned by [initializeOpenTelemetrySdk]; tests can bind [OpenTelemetry.noop] to verify
 * production module graphs without touching global OTel state. Install it as an override to replace any legacy
 * application-provided binding. Keeping initialization outside [configure] ensures Guice module inspection and
 * overrides have no process-global side effects.
 */
class OpenTelemetryModule(private val openTelemetry: OpenTelemetry) : KAbstractModule() {
  override fun configure() {
    bind<OpenTelemetry>().toInstance(openTelemetry)
  }
}
