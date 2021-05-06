package misk.web.interceptors

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<InternalErrorInterceptorFactory>()

@Singleton
class InternalErrorInterceptorFactory @Inject constructor() : NetworkInterceptor.Factory {
  override fun create(action: Action): NetworkInterceptor? {
    return INTERCEPTOR
  }

  private companion object {
    val INTERCEPTOR = object : NetworkInterceptor {
      override fun intercept(chain: NetworkChain) {
        try {
          chain.proceed(chain.httpCall)
        } catch (throwable: Throwable) {
          logger.error(throwable) { "${chain.httpCall.url} failed; returning an HTTP 500 error" }
          chain.httpCall.statusCode = 500
          chain.httpCall.takeResponseBody()?.use { sink ->
            chain.httpCall.setResponseHeader("Content-Type", "text/plain; charset=utf-8")
            sink.writeUtf8("Internal server error")
          }
        }
      }
    }
  }
}
