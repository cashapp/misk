package misk.tracing

import misk.inject.KAbstractModule
import misk.tracing.backends.TracingBackendModule
import misk.tracing.interceptors.TracingMethodInterceptorModule

class TracingModule(val config: TracingConfig) : KAbstractModule() {
  override fun configure() {
    install(TracingBackendModule(config))
    install(TracingMethodInterceptorModule())
  }
}
