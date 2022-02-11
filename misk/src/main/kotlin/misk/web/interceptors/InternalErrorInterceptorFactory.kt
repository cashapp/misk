package misk.web.interceptors

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.extractors.RequestBodyException
import wisp.logging.getLogger
import java.io.IOException
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
          if (throwable is RequestBodyException) {
            chain.httpCall.statusCode = 499
            logger.info(throwable) { "${chain.httpCall.url.redact()} failed; returning HTTP 499" }
          } else {
            chain.httpCall.statusCode = 500
            logger.error(throwable) { "${chain.httpCall.url.redact()} failed; returning HTTP 500" }
          }
          chain.httpCall.takeResponseBody()?.use { sink ->
            chain.httpCall.setResponseHeader("Content-Type", "text/plain; charset=utf-8")
            sink.writeUtf8("Internal server error")
          }
        }
      }
    }
  }
}
