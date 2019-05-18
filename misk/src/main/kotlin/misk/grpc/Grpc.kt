package misk.grpc

import com.squareup.wire.ProtoAdapter
import misk.web.ResponseBody
import misk.web.marshal.GenericMarshallers
import misk.web.marshal.GenericUnmarshallers
import misk.web.marshal.Marshaller
import misk.web.marshal.Unmarshaller
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

@Singleton
class GrpcMarshallerFactory @Inject constructor() : Marshaller.Factory {
  override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? {
    if (mediaType.type() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.type() ||
        mediaType.subtype() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.subtype()) {
      return null
    }

    val responseType = Marshaller.actualResponseType(type)
    if (GenericMarshallers.canHandle(responseType)) return null

    val elementType = type.streamElementType()

    @Suppress("UNCHECKED_CAST") // Guarded by reflection.
    return if (elementType != null) {
      GrpcStreamMarshaller(ProtoAdapter.get(elementType as Class<Any>)) as Marshaller<Any>
    } else {
      GrpcSingleMarshaller<Any>(ProtoAdapter.get(responseType as Class<Any>))
    }
  }
}

internal class GrpcSingleMarshaller<T>(val adapter: ProtoAdapter<T>) : Marshaller<T> {
  override fun contentType() = MediaTypes.APPLICATION_GRPC_MEDIA_TYPE

  override fun responseBody(o: T): ResponseBody {
    return object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        val writer = GrpcWriter.get(sink, adapter)
        writer.writeMessage(o)
      }
    }
  }
}

internal class GrpcStreamMarshaller<T>(val adapter: ProtoAdapter<T>) : Marshaller<GrpcReceiveChannel<T>> {
  override fun contentType() = MediaTypes.APPLICATION_GRPC_MEDIA_TYPE

  override fun responseBody(o: GrpcReceiveChannel<T>): ResponseBody {
    return object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        GrpcWriter.get(sink, adapter).use { writer ->
          o.consumeEach { message ->
            writer.writeMessage(message)
            writer.flush()
          }
        }
      }
    }
  }
}

@Singleton
class GrpcUnmarshallerFactory @Inject constructor() : Unmarshaller.Factory {
  override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
    if (mediaType.type() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.type() ||
        mediaType.subtype() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.subtype()) {
      return null
    }

    if (GenericUnmarshallers.canHandle(type)) return null

    val elementType = type.streamElementType()

    @Suppress("UNCHECKED_CAST") // Guarded by reflection.
    return if (elementType != null) {
      GrpcStreamUnmarshaller(ProtoAdapter.get(elementType as Class<Any>))
    } else {
      GrpcSingleUnmarshaller(ProtoAdapter.get(type.javaType as Class<Any>))
    }
  }
}

internal class GrpcSingleUnmarshaller<T>(val adapter: ProtoAdapter<T>) : Unmarshaller {
  override fun unmarshal(source: BufferedSource): Any? {
    return GrpcReader.get(source, adapter).readMessage()
  }
}

internal class GrpcStreamUnmarshaller<T>(val adapter: ProtoAdapter<T>) : Unmarshaller {
  override fun unmarshal(source: BufferedSource): GrpcReceiveChannel<T> {
    return object : GrpcReceiveChannel<T> {
      val grpcReader = GrpcReader.get(source, adapter)

      override fun receiveOrNull(): T? {
        return grpcReader.readMessage()
      }
    }
  }
}

/**
 * Returns the channel element type, like `MyRequest` if this is `Channel<MyRequest>`. Returns null
 * if this is not a channel.
 */
private fun KType.streamElementType(): Type? {
  val parameterizedType = javaType as? ParameterizedType ?: return null
  if (parameterizedType.rawType != GrpcReceiveChannel::class.java &&
      parameterizedType.rawType != GrpcSendChannel::class.java) return null
  return parameterizedType.actualTypeArguments[0]
}
