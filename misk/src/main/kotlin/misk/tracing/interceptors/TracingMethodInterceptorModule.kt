package misk.tracing.interceptors

import com.google.inject.matcher.Matchers
import io.opentracing.Tracer
import misk.inject.KAbstractModule
import misk.tracing.Trace

class TracingMethodInterceptorModule : KAbstractModule() {
  override fun configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(Trace::class.java),
        TracingMethodInterceptor(getProvider(Tracer::class.java)))
  }
}
