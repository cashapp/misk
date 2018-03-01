package misk.web.interceptors

import misk.Action
import misk.NetworkChain
import misk.NetworkInterceptor
import misk.web.Response
import misk.web.ResponseBody
import okhttp3.Headers
import okio.BufferedSink
import javax.inject.Singleton

@Singleton
class InternalErrorInterceptorFactory : NetworkInterceptor.Factory {
  override fun create(action: Action): NetworkInterceptor? {
    return INTERCEPTOR
  }

  private companion object {
    val HEADERS: Headers = Headers.Builder()
        .set("Content-Type", "text/plain; charset=utf-8")
        .build()

    const val STATUS_CODE = 500

    val BODY = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8("Internal server error")
      }
    }

    val INTERCEPTOR = object : NetworkInterceptor {
      override fun intercept(chain: NetworkChain): Response<*> {
        return try {
          chain.proceed(chain.request)
        } catch (_: Throwable) {
          Response(BODY, HEADERS, STATUS_CODE)
        }
      }
    }
  }
}
