package misk.web.marshal

import com.squareup.wire.ProtoAdapter
import misk.web.ResponseBody
import misk.web.marshal.Marshaller.Companion.actualResponseType
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

class ProtobufMarshaller<T>(val adapter: ProtoAdapter<T>) : Marshaller<T> {
  override fun contentType() = MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE

  override fun responseBody(o: T) = object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      adapter.encode(sink, o)
    }
  }

  @Singleton
  class Factory @Inject constructor() : Marshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? {
      if (mediaType.type != MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE.subtype
      ) {
        return null
      }

      val responseType = actualResponseType(type)
      if (GenericMarshallers.canHandle(responseType)) return null
      @Suppress("UNCHECKED_CAST")
      return ProtobufMarshaller<Any>(ProtoAdapter.get(responseType as Class<Any>))
    }
  }
}

class ProtobufUnmarshaller(val adapter: ProtoAdapter<Any>) : Unmarshaller {
  override fun unmarshal(requestHeaders: Headers, source: BufferedSource) = adapter.decode(source)

  @Singleton
  class Factory @Inject constructor() : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType.type != MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE.subtype
      ) {
        return null
      }

      if (GenericUnmarshallers.canHandle(type)) return null
      @Suppress("UNCHECKED_CAST")
      return ProtobufUnmarshaller(ProtoAdapter.get(type.javaType as Class<Any>))
    }
  }
}
