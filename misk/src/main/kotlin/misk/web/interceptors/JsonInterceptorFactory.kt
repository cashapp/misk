package misk.web.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.web.JsonResponseBody
import misk.web.Response
import misk.web.ResponseBody
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import okio.BufferedSink
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

/** Converts a `Response<T>` to a `Response<WritableBody>` by encoding the `T` to JSON. */
@Singleton
internal class JsonInterceptorFactory @Inject constructor(
    private val moshi: Moshi
) : Interceptor.Factory {
    override fun create(action: Action): Interceptor? {
        if (action.function.findAnnotation<JsonResponseBody>() == null) return null

        val responseType = when {
            action.returnType.rawType == Response::class.java -> {
                (action.returnType.type as ParameterizedType).actualTypeArguments[0]
            }
            else -> action.returnType.type
        }
        val adapter = moshi.adapter<Any>(responseType)
        return JsonInterceptor<Any>(adapter)
    }

    class JsonInterceptor<T>(val jsonAdapter: JsonAdapter<T>) : Interceptor {
        override fun intercept(chain: Chain): Any? {
            @Suppress("UNCHECKED_CAST")
            val responseOfT: Response<T> = chain.proceed(chain.args) as Response<T>

            val body = object : ResponseBody {
                override fun writeTo(sink: BufferedSink) {
                    jsonAdapter.toJson(sink, responseOfT.body)
                }
            }

            val headers = responseOfT.headers.newBuilder()
                    .set("Content-Type", "application/json; charset=utf-8")
                    .build()

            return Response(body, headers, responseOfT.statusCode)
        }
    }
}
