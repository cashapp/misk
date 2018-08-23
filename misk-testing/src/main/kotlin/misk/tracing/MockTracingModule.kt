package misk.tracing

import com.google.inject.Provides
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import io.opentracing.util.GlobalTracer
import misk.inject.KAbstractModule
import javax.inject.Singleton


class MockTracingModule : KAbstractModule() {
  companion object {
    val tracer : MockTracer = MockTracer()
  }

  override fun configure() {
    if (!GlobalTracer.isRegistered()) {
      GlobalTracer.register(tracer)
    }
  }

  @Provides
  @Singleton
  fun mockTracer() : MockTracer {
    return tracer
  }

  @Provides
  @Singleton
  fun tracer() : Tracer {
    return tracer
  }
}
