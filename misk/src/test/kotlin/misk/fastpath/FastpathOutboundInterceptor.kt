package misk.fastpath

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/** OkHttp interceptor that sends and receives fastpath headers. */
@Singleton
class FastpathOutboundInterceptor @Inject constructor(
  private val fastpath: ClientFastpath
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    if (!fastpath.collecting) {
      return chain.proceed(chain.request())
    }

    val request = chain.request().newBuilder()
        .addHeader("fastpath", "use-the-fastpath-luke")
        .build()

    val response = chain.proceed(request)

    fastpath.events += response.headers.values("fastpath-events")

    return response
  }
}
