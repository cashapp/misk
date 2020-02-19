package misk.tracing.backends.jaeger

import com.google.common.util.concurrent.AbstractIdleService
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("UnstableApiUsage") // Guava's Service is @Beta.
internal class JaegerTracingService @Inject internal constructor(
  private val tracer: Tracer
) : AbstractIdleService() {
  override fun startUp() {
    GlobalTracer.registerIfAbsent(tracer)
  }

  override fun shutDown() {
    tracer.close()
  }
}
