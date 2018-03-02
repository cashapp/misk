package misk.web.interceptors

import com.google.inject.Inject
import com.google.inject.Singleton
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import misk.Action
import misk.NetworkChain
import misk.NetworkInterceptor
import misk.logging.getLogger
import misk.web.Response

private val logger = getLogger<TracingInterceptor>()

/**
 * Enables distributed tracing on all web actions, if a client has installed a tracer.
 */
internal class TracingInterceptor internal constructor(private val tracer: Tracer):
    NetworkInterceptor {
  @Singleton
  class Factory : NetworkInterceptor.Factory {
    @Inject(optional=true) var tracer: Tracer? = null

    override fun create(action: Action): NetworkInterceptor? {
      // NOTE(nb): returning null ensures interceptor is filtered out when generating interceptors to
      // apply for a specific action. See WebActionModule for implementation details
      return if (tracer != null) TracingInterceptor(tracer!!) else null
    }
  }

  override fun intercept(chain: NetworkChain): Response<*> {
    val scope = tracer.buildSpan("${chain.action}").startActive(true)
    return scope.use {
      try {
        val result = chain.proceed(chain.request)
        Tags.HTTP_STATUS.set(scope.span(), result.statusCode)
        if (result.statusCode > 399) {
          Tags.ERROR.set(scope.span(), true)
        }
        result
      } catch (exception: Exception) {
        Tags.ERROR.set(scope.span(), true)
        throw exception
      }
    }
  }
}
