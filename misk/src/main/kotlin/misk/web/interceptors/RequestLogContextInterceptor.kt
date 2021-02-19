package misk.web.interceptors

import misk.Action
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import org.slf4j.MDC
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServletRequest

/**
 * [RequestLogContextInterceptor] puts information about the current request into the
 * logging MDC so it can be included in structured logs
 */
internal class RequestLogContextInterceptor private constructor(
  private val action: Action,
  private val currentCaller: ActionScoped<MiskCaller?>,
  private val currentRequest: ActionScoped<HttpServletRequest>
) : NetworkInterceptor {

  override fun intercept(chain: NetworkChain) {
    val request = currentRequest.get()
    return try {
      MDC.put(MDC_ACTION, action.name)
      MDC.put(MDC_CALLING_PRINCIPAL, currentCaller.get()?.principal ?: "unknown")
      MDC.put(MDC_PROTOCOL, request.protocol)
      MDC.put(MDC_REMOTE_ADDR, "${request.remoteAddr}:${request.remotePort}")
      MDC.put(MDC_REQUEST_URI, request.requestURI)
      MDC.put(MDC_HTTP_METHOD, request.method)
      chain.proceed(chain.httpCall)
    } finally {
      allContextNames.forEach { MDC.remove(it) }
    }
  }

  @Singleton
  class Factory @Inject internal constructor(
    private val currentCaller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val currentRequest: @JvmSuppressWildcards ActionScoped<HttpServletRequest>
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action) =
      RequestLogContextInterceptor(action, currentCaller, currentRequest)
  }

  internal companion object {
    const val MDC_CALLING_PRINCIPAL = "calling_principal"
    const val MDC_REMOTE_ADDR = "remote_addr"
    const val MDC_ACTION = "action"
    const val MDC_REQUEST_URI = "request_uri"
    const val MDC_PROTOCOL = "protocol"
    const val MDC_HTTP_METHOD = "http_method"

    val allContextNames = listOf(
      MDC_ACTION,
      MDC_CALLING_PRINCIPAL,
      MDC_HTTP_METHOD,
      MDC_PROTOCOL,
      MDC_REMOTE_ADDR,
      MDC_REQUEST_URI
    )
  }
}
