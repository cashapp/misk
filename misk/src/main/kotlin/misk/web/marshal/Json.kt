package misk.web.marshal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.web.ResponseBody
import misk.web.actions.WebSocketListener
import misk.web.marshal.Marshaller.Companion.actualResponseType
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import javax.inject.Inject
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

class JsonMarshaller<T>(val adapter: JsonAdapter<T>) : Marshaller<T> {
  override fun contentType() = MediaTypes.APPLICATION_JSON_MEDIA_TYPE

  override fun responseBody(o: T) = object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      adapter.toJson(sink, o)
    }
  }

  class Factory @Inject internal constructor(val moshi: Moshi) : Marshaller.Factory {
    override fun create(
      mediaType: MediaType,
      type: KType,
      factories: List<Marshaller.Factory>
    ): Marshaller<Any>? {
      if (mediaType.type() != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.type() ||
          mediaType.subtype() != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.subtype() ||
          type.classifier == WebSocketListener::class) {
        return null
      }

      val responseType = actualResponseType(type)
      if (GenericMarshallers.canHandle(responseType)) return null
      return JsonMarshaller<Any>(moshi.adapter<Any>(responseType))
    }
  }
}

class JsonUnmarshaller<out T>(val adapter: JsonAdapter<Any>) : Unmarshaller<T> {
  override fun unmarshal(source: BufferedSource): T? {
    return adapter.fromJson(source) as T
  }

  class Factory @Inject internal constructor(val moshi: Moshi) : Unmarshaller.Factory {
    override fun <T> create(mediaType: MediaType, type: KType): Unmarshaller<T>? {
      if (mediaType.type() != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.type() ||
          mediaType.subtype() != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.subtype() ||
          type.classifier == WebSocketListener::class) return null

      if (GenericUnmarshallers.canHandle(type)) return null
      return JsonUnmarshaller(moshi.adapter<Any>(type.javaType))
    }
  }
}
