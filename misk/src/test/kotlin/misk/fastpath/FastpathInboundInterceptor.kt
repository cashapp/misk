package misk.fastpath

import javax.inject.Inject
import javax.inject.Singleton
import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import okhttp3.Headers

/** Jetty interceptor that receives and sends fastpath headers. */
class FastpathInboundInterceptor(
  private val fastpath: ServerFastpath
) : ApplicationInterceptor {

  @Singleton
  class Factory @Inject constructor(
    private val fastpath: ServerFastpath
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action) = FastpathInboundInterceptor(fastpath)
  }

  override fun intercept(chain: Chain): Any {
    if (chain.httpCall.requestHeaders["fastpath"] != "use-the-fastpath-luke") {
      return chain.proceed(chain.args) // No fastpath.
    }

    fastpath.collecting = true
    val result = chain.proceed(chain.args)
    val headersBuilder = Headers.Builder()
    for (event in fastpath.events) {
      headersBuilder.add("fastpath-events", event)
    }
    chain.httpCall.addResponseHeaders(headersBuilder.build())
    return result
  }
}
