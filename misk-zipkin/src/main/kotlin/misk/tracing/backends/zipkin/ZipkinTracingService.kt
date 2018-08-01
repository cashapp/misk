package misk.tracing.backends.zipkin

import com.google.common.util.concurrent.AbstractIdleService
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ZipkinTracingService @Inject internal constructor(
  private val tracer: Tracer
) : AbstractIdleService() {
  override fun startUp() = GlobalTracer.register(tracer)
  override fun shutDown() {
  }
}