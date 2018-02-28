package misk.tracing.interceptors

import io.opentracing.Scope
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import misk.logging.getLogger
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import javax.inject.Inject
import javax.inject.Provider

private val logger = getLogger<TracingMethodInterceptor>()

class TracingMethodInterceptor @Inject internal constructor(
  private val tracerProvider: Provider<Tracer>
) : MethodInterceptor {
  override fun invoke(invocation: MethodInvocation): Any? {
    var result: Any? = null
    var scope: Scope? = null
    val tracer = tracerProvider.get()

    try {
      scope = tracer.buildSpan("${invocation.javaClass.simpleName}.${invocation.method.name}").startActive(true)
      result = invocation.proceed()
    } catch (exception: Exception) {
      logger.warn("failed attempting to trace method", exception)
      if (scope != null) Tags.ERROR.set(scope.span(), true)
    } finally {
      if (scope != null) scope.close()
    }

    return result
  }
}
