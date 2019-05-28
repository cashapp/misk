package misk.web.extractors

import com.squareup.wire.ProtoAdapter
import misk.grpc.GrpcSendChannel
import misk.grpc.GrpcWriter
import misk.web.PathPattern
import misk.web.Request
import misk.web.ResponseBody
import misk.web.actions.WebAction
import okio.BufferedSink
import java.lang.reflect.ParameterizedType
import java.util.regex.Matcher
import javax.inject.Inject
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType

internal class GrpcResponseChannelParameterExtractor<T>(
  private val adapter: ProtoAdapter<T>
) : ParameterExtractor {
  override fun extract(
    webAction: WebAction,
    request: Request,
    responseBodySink: BufferedSink?,
    pathMatcher: Matcher
  ): Any? {
    return GrpcResponseBody(adapter, responseBodySink!!)
  }

  class GrpcResponseBody<T>(
    val adapter: ProtoAdapter<T>,
    responseBodySink: BufferedSink
  ) : ResponseBody, GrpcSendChannel<T> {
    var grpcWriter = GrpcWriter.get(responseBodySink, adapter)

    override fun writeTo(sink: BufferedSink) {
    }

    override fun send(message: T) {
      // TODO: make sure we called writeTo already yo
      grpcWriter.writeMessage(message)
      grpcWriter.flush()
    }

    override fun close() {
      grpcWriter.close()
    }
  }

  class Factory @Inject internal constructor() : ParameterExtractor.Factory {
    override fun create(
      function: KFunction<*>,
      parameter: KParameter,
      pathPattern: PathPattern
    ): ParameterExtractor? {
      val parameterizedType = parameter.type.javaType as? ParameterizedType ?: return null
      if (parameterizedType.rawType != GrpcSendChannel::class.java) return null
      val streamElementType = parameterizedType.actualTypeArguments[0]
      @Suppress("UNCHECKED_CAST")
      val protoAdapter: ProtoAdapter<Any> = ProtoAdapter.get(streamElementType as Class<Any>)
      return GrpcResponseChannelParameterExtractor(protoAdapter)
    }
  }
}