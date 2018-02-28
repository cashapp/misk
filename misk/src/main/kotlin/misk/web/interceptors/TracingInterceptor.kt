package misk.web.interceptors

import com.google.inject.Inject
import com.google.inject.Singleton
import io.opentracing.Scope
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import misk.Action
import misk.Chain
import misk.Interceptor
import misk.logging.getLogger

private val logger = getLogger<TracingInterceptor>()

/**
 * Enables distributed tracing on all web actions, if a client has installed a tracer.
 */
internal class TracingInterceptor internal constructor(private val tracer: Tracer): Interceptor {
  @Singleton
  class Factory : Interceptor.Factory {
    @Inject(optional=true) var tracer: Tracer? = null

    override fun create(action: Action): Interceptor? {
      // NOTE(nb): returning null ensures interceptor is filtered out when generating interceptors to
      // apply for a specific action. See WebActionModule for implementation details
      return if (tracer != null) TracingInterceptor(tracer!!) else null
    }
  }

  override fun intercept(chain: Chain): Any? {
    var result: Any? = null

    tracer.buildSpan("${chain.action}").startActive(true).use { result = chain.proceed(chain.args) }

    return result
  }
}