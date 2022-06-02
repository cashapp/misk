package misk.web.interceptors

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.WebConfig
import okio.GzipSource
import okio.buffer
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inflates a gzipped compressed request. The interceptor should be installed if the default
 * [GzipHandler] for handling such requests was opted out via [WebConfig.gunzip].
 */
class GunzipRequestBodyInterceptor private constructor() : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val contentEncoding = chain.httpCall.requestHeaders[CONTENT_ENCODING]
      ?: return chain.proceed(chain.httpCall)
    if (contentEncoding.lowercase() == GZIP || COMMA_GZIP.matches(contentEncoding)) {
      chain.httpCall.takeRequestBody()?.let {
        chain.httpCall.putRequestBody(GzipSource(it).buffer())
      }
    }
    chain.proceed(chain.httpCall)
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
    private const val GZIP = "gzip"
    private val COMMA_GZIP = Regex(".*, *gzip")
  }
}
