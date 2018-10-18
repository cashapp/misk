package misk.tracing.backends.jaeger

import com.google.common.util.concurrent.AbstractIdleService
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class JaegerTracingService @Inject internal constructor(
  private val tracer: Tracer
) : AbstractIdleService() {
  override fun startUp() {
    // In case startUp is called multiple times, ensure tracer is only registered once
    if (!GlobalTracer.isRegistered()) {
      GlobalTracer.register(tracer)
    }
  }

  override fun shutDown() {
  }
}