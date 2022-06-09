package misk.web.interceptors

import misk.Action
import misk.web.HttpCall
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import okio.GzipSource
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inflates a gzipped compressed request.
 */
internal class GunzipRequestBodyInterceptor private constructor() : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val httpCall = chain.httpCall
    val contentEncoding = httpCall.requestHeaders[CONTENT_ENCODING]
      ?: return chain.proceed(httpCall)
    if (contentEncoding.lowercase() == GZIP) {
      httpCall.takeRequestBody()?.let {
        httpCall.putRequestBody(GzipSource(it).buffer())
      }
      modifyRequestHeaders(httpCall)
    }
    chain.proceed(httpCall)
  }

  private fun modifyRequestHeaders(httpCall: HttpCall) {
    httpCall.computeRequestHeader(CONTENT_ENCODING) {
      Pair(X_CONTENT_ENCODING, GZIP)
    }
    httpCall.computeRequestHeader(CONTENT_LENGTH) { value ->
      if (value == null) {
        null
      } else {
        Pair(X_CONTENT_LENGTH, value)
      }
    }
  }

  @Singleton
  class Factory @Inject internal constructor() : NetworkInterceptor.Factory {
    override fun create(action: Action) = INTERCEPTOR

    private companion object {
      private val INTERCEPTOR = GunzipRequestBodyInterceptor()
    }
  }

  private companion object {
    private const val CONTENT_ENCODING = "Content-Encoding"
    private const val CONTENT_LENGTH = "Content-Length"
    private const val X_CONTENT_ENCODING = "X-Content-Encoding"
    private const val X_CONTENT_LENGTH = "X-Content-Length"
    private const val GZIP = "gzip"
  }
}
