package misk.web

import misk.Action
import misk.logging.getLogger
import misk.web.actions.WebAction
import misk.web.interceptors.LogNetworkInterceptorChain
import kotlin.reflect.full.findAnnotation

internal class RealNetworkChain(
  override val action: Action,
  override val webAction: WebAction,
  override val httpCall: HttpCall,
  private val interceptors: List<NetworkInterceptor>,
  private val index: Int = 0
) : NetworkChain {
  override fun proceed(httpCall: HttpCall) {
    check(index < interceptors.size) { "final interceptor must be terminal" }

    val next = RealNetworkChain(action, webAction, httpCall, interceptors, index + 1)
    val interceptor = interceptors[index]

    logCallingInterceptor(interceptor)
    val result = interceptor.intercept(next)
    logInterceptorReturned(interceptor)

    return result
  }

  private fun logCallingInterceptor(interceptor: NetworkInterceptor) {
    val loggingAnnotation = webAction::class.findAnnotation<LogNetworkInterceptorChain>()
    if (loggingAnnotation != null && loggingAnnotation.logBefore) {
      logger.info("Interceptor about to process: {}", interceptor)
    }
  }

  private fun logInterceptorReturned(interceptor: NetworkInterceptor) {
    val loggingAnnotation = webAction::class.findAnnotation<LogNetworkInterceptorChain>()
    if (loggingAnnotation != null && loggingAnnotation.logAfter) {
      logger.info("Interceptor finished processing: {}", interceptor)
    }
  }

  companion object {
    private val logger = getLogger<RealNetworkChain>()
  }
}
