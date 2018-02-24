package misk.tracing

import misk.inject.KAbstractModule
import misk.tracing.backends.jaeger.JaegerBackendModule

class TracingModule(val config: TracingConfig) : KAbstractModule() {
 override fun configure() {
  if (config.tracer.equals("jaeger", true)) {
   install(JaegerBackendModule(config.backends.jaeger))
   return
  }

  throw IllegalArgumentException("No backend for '${config.tracer}' tracer. Ensure that " +
    "tracer name is spelled correctly and that a backend module exists for that tracer.")
 }
}
