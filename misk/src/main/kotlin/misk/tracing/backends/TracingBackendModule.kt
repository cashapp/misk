package misk.tracing.backends

import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.tracing.TracingConfig
import misk.tracing.backends.jaeger.JaegerBackendModule
import misk.tracing.backends.noop.NoopTracerBackendModule

private val logger = getLogger<TracingBackendModule>()

class TracingBackendModule(val config: TracingConfig) : KAbstractModule() {
  override fun configure() {
    if (config.backends.jaeger != null) {
      install(JaegerBackendModule(config.backends.jaeger))
    } else {
      install(NoopTracerBackendModule())
      logger.info("No tracing backend configured. NoopTracer installed.")
    }
  }
}