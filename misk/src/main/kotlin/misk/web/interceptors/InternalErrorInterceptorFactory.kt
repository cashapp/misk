package misk.web.interceptors

import misk.Action
import misk.logging.getLogger
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.toResponseBody
import okhttp3.Headers
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<InternalErrorInterceptorFactory>()

@Singleton
class InternalErrorInterceptorFactory @Inject constructor() : NetworkInterceptor.Factory {
  override fun create(action: Action): NetworkInterceptor? {
    return INTERCEPTOR
  }

  private companion object {
    val HEADERS: Headers = Headers.Builder()
        .set("Content-Type", "text/plain; charset=utf-8")
        .build()

    const val STATUS_CODE = 500

    val BODY = "Internal server error".toResponseBody()

    val INTERCEPTOR = object : NetworkInterceptor {
      override fun intercept(chain: NetworkChain): Response<*> {
        return try {
          chain.proceed(chain.request)
        } catch (throwable: Throwable) {
          logger.error(throwable) { "${chain.request.url} failed; returning an HTTP 500 error" }
          Response(BODY, HEADERS, STATUS_CODE)
        }
      }
    }
  }
}
