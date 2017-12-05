package misk.web.interceptors

import com.google.common.base.Stopwatch
import misk.Action
import misk.Chain
import misk.Interceptor
import misk.logging.getLogger
import javax.inject.Singleton

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Prints timing for calling into an action. Doesn't count time writing the response to the remote
 * client.
 */
internal class RequestLoggingInterceptor : Interceptor {
  @Singleton
  class Factory : Interceptor.Factory {
    override fun create(action: Action): Interceptor? {
      // TODO: return null if we don't want to log the request
      return RequestLoggingInterceptor()
    }
  }

  override fun intercept(chain: Chain): Any? {
    val stopwatch = Stopwatch.createStarted()
    val result = chain.proceed(chain.args)
    logger.debug { "action ${chain.action} took $stopwatch" }
    return result
  }
}
