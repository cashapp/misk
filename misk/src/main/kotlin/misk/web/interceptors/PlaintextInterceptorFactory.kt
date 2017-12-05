package misk.web.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.web.PlaintextResponseBody
import misk.web.Response
import misk.web.ResponseBody
import okio.BufferedSink
import kotlin.reflect.full.findAnnotation

/**
 * Handles actions annotated [PlaintextResponseBody] by converting the `Response` to a
 * `Response<WritableBody>` by calling [Any.toString] on the `T`.
 */
object PlaintextInterceptorFactory : Interceptor.Factory {
  val interceptor = object : Interceptor {
    override fun intercept(chain: Chain): Any? {
      val response: Response<*> = chain.proceed(chain.args) as Response<*>

      val bodyStream = object : ResponseBody {
        override fun writeTo(sink: BufferedSink) {
          sink.writeUtf8(response.body.toString())
        }
      }

      val headers = response.headers.newBuilder()
          .set("Content-Type", "text/plain; charset=utf-8")
          .build()

      return Response(bodyStream, headers, response.statusCode)
    }
  }

  override fun create(action: Action): Interceptor? {
    if (action.function.findAnnotation<PlaintextResponseBody>() == null) return null
    return interceptor
  }
}

