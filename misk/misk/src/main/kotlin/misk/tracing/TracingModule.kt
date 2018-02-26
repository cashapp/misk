package misk.tracing

import misk.inject.KAbstractModule
import misk.tracing.backends.jaeger.JaegerBackendModule

class TracingModule(val config: TracingConfig) : KAbstractModule() {
  override fun configure() {
    if (config.backends.jaeger != null) {
      install(JaegerBackendModule(config.backends.jaeger))
      return
    }

    throw IllegalStateException("No backends configured for tracing. Please update your yaml "
      + "configuration.")
  }
}
