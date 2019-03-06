package misk.web.marshal

import com.squareup.wire.ProtoAdapter
import misk.grpc.GrpcReader
import misk.grpc.GrpcWriter
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

internal class GrpcMarshaller<T>(val adapter: ProtoAdapter<T>) : Marshaller<T> {
  override fun contentType() = MediaTypes.APPLICATION_GRPC_MEDIA_TYPE

  override fun responseBody(o: T) = object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      val writer = GrpcWriter.get(sink, adapter)
      writer.writeMessage(o)
      writer.flush()
    }
  }

  @Singleton
  class Factory @Inject constructor() : Marshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? {
      if (mediaType.type() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.type() ||
          mediaType.subtype() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.subtype()) {
        return null
      }

      val responseType = Marshaller.actualResponseType(type)
      if (GenericMarshallers.canHandle(responseType)) return null
      @Suppress("UNCHECKED_CAST")
      return GrpcMarshaller<Any>(ProtoAdapter.get(responseType as Class<Any>))
    }
  }
}

internal class GrpcUnmarshaller<T>(val adapter: ProtoAdapter<T>) : Unmarshaller {
  override fun unmarshal(source: BufferedSource): Any? {
    // TODO(jwilson): wire through the media type in the unmarshal call for gzip
    val reader = GrpcReader.get(source, adapter)
    return reader.readMessage()
  }

  @Singleton
  class Factory @Inject constructor() : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType.type() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.type() ||
          mediaType.subtype() != MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.subtype()) {
        return null
      }

      if (GenericUnmarshallers.canHandle(type)) return null
      @Suppress("UNCHECKED_CAST")
      return GrpcUnmarshaller(ProtoAdapter.get(type.javaType as Class<Any>))
    }
  }
}
