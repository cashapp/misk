package misk.tracing.backends.noop

import com.google.inject.Provides
import com.google.inject.Singleton
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracerFactory
import misk.inject.KAbstractModule

class NoopTracingModule : KAbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun noopTracer() : Tracer {
    return NoopTracerFactory.create()
  }
}