package misk.web.marshal

import misk.web.marshal.Marshaller.Companion.actualResponseType
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSource
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType

object PlainTextMarshaller : Marshaller<Any> {
  override fun contentType() = MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE
  override fun responseBody(o: Any) = o.toString().toResponseBody()

  @Singleton
  class Factory @Inject constructor() : Marshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? {
      if (mediaType.type != MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE.subtype
      ) {
        return null
      }

      if (GenericMarshallers.canHandle(actualResponseType(type))) return null
      return PlainTextMarshaller
    }
  }
}

object PlainTextUnmarshaller {
  object ToString : Unmarshaller {
    override fun unmarshal(requestHeaders: Headers, source: BufferedSource) = source.readUtf8()
  }

  object ToByteString : Unmarshaller {
    override fun unmarshal(requestHeaders: Headers, source: BufferedSource) =
      source.readByteString()
  }

  class Factory @Inject constructor() : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType.type != MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE.subtype
      ) return null

      if (GenericUnmarshallers.canHandle(type)) return null

      return when (type) {
        String::class -> ToString
        ByteString::class -> ToByteString
        else -> throw IllegalArgumentException("no plain/text unmarshaller for $type")
      }
    }
  }
}
