package misk.web.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.web.Response
import misk.web.ResponseBody
import okhttp3.Headers
import okio.BufferedSink
import javax.inject.Singleton

@Singleton
class InternalErrorInterceptorFactory : Interceptor.Factory {
  override fun create(action: Action): Interceptor? {
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

    val INTERCEPTOR = object : Interceptor {
      override fun intercept(chain: Chain): Any? {
        return try {
          chain.proceed(chain.args)
        } catch (_: Throwable) {
          Response(BODY, HEADERS, STATUS_CODE)
        }
      }
    }
  }
}
