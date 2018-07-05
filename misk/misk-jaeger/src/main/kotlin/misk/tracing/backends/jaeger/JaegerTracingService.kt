package misk.tracing.backends.jaeger

import com.google.common.util.concurrent.AbstractIdleService
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import javax.inject.Inject

internal class JaegerTracingService @Inject internal constructor(
  private val tracer: Tracer
) : AbstractIdleService() {
  override fun startUp() = GlobalTracer.register(tracer)
  override fun shutDown() {
  }
}