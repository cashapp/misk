package misk.web.interceptors

import com.google.common.base.Stopwatch
import misk.Action
import misk.NetworkChain
import misk.NetworkInterceptor
import misk.logging.getLogger
import misk.web.Response
import javax.inject.Singleton

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Prints timing for calling into an action. Doesn't count time writing the response to the remote
 * client.
 */
internal class RequestLoggingInterceptor : NetworkInterceptor {
  @Singleton
  class Factory : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      // TODO: return null if we don't want to log the request
      return RequestLoggingInterceptor()
    }
  }

  override fun intercept(chain: NetworkChain): Response<*> {
    val stopwatch = Stopwatch.createStarted()
    val result = chain.proceed(chain.request)
    logger.debug { "action ${chain.action} took $stopwatch" }
    return result
  }
}
