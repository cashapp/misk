package misk.web.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.web.ProtobufResponseBody
import misk.web.Response
import misk.web.ResponseBody
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import okio.BufferedSink
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

@Singleton
internal class ProtobufInterceptorFactory<M : Message<M, B>, B : Message.Builder<M, B>> : Interceptor.Factory {
    override fun create(action: Action): ProtobufInterceptor<*>? {
        if (action.function.findAnnotation<ProtobufResponseBody>() == null) return null

        // Verify the return type is a subtype of [Message<*, *>]
        action.returnType.getSupertype(Message::class.java)

        val protoAdapter = ProtoAdapter.newMessageAdapter(action.returnType.rawType as Class<M>)
        return ProtobufInterceptor(protoAdapter)
    }

    internal class ProtobufInterceptor<T>(val protoAdapter: ProtoAdapter<T>) : Interceptor {
        override fun intercept(chain: Chain): Any? {
            @Suppress("UNCHECKED_CAST")
            val responseOfT: Response<T> = chain.proceed(chain.args) as Response<T>

            val body = object : ResponseBody {
                override fun writeTo(sink: BufferedSink) {
                    protoAdapter.encode(sink, responseOfT.body)
                }
            }

            val headers = responseOfT.headers.newBuilder()
                    .set("Content-Type", "application/x-protobuf")
                    .build()

            return Response(body, headers, responseOfT.statusCode)
        }
    }
}
