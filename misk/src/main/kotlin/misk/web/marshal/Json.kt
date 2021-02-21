package misk.web.marshal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
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

class JsonMarshaller<T>(val adapter: JsonAdapter<T>) : Marshaller<T> {
  override fun contentType() = MediaTypes.APPLICATION_JSON_MEDIA_TYPE

  override fun responseBody(o: T) = object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      adapter.toJson(sink, o)
    }
  }

  @Singleton
  class Factory @Inject internal constructor(val moshi: Moshi) : Marshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? {
      if (mediaType.type != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.subtype
      ) {
        return null
      }

      val responseType = actualResponseType(type)
      if (GenericMarshallers.canHandle(responseType)) return null
      return JsonMarshaller<Any>(moshi.adapter<Any>(responseType))
    }
  }
}

class JsonUnmarshaller(val adapter: JsonAdapter<Any>) : Unmarshaller {
  override fun unmarshal(requestHeaders: Headers, source: BufferedSource) = adapter.fromJson(source)

  @Singleton
  class Factory @Inject internal constructor(val moshi: Moshi) : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType.type != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.subtype
      ) return null

      if (GenericUnmarshallers.canHandle(type)) return null
      return JsonUnmarshaller(moshi.adapter<Any>(type.javaType))
    }
  }
}
