package misk.web.marshal

import misk.web.ResponseBody
import misk.web.actions.WebSocketListener
import misk.web.marshal.Marshaller.Companion.actualResponseType
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okio.BufferedSink
import kotlin.reflect.KType

object PlainTextMarshaller : Marshaller<Any> {
  override fun contentType() = MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE
  override fun responseBody(o: Any) = object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.writeString(o.toString(), Charsets.UTF_8)
    }
  }

  class Factory : Marshaller.Factory {
    override fun create(
      mediaType: MediaType,
      type: KType,
      factories: List<Marshaller.Factory>
    ): Marshaller<Any>? {
      if (mediaType.type() != MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE.type() ||
          mediaType.subtype() != MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE.subtype() ||
          type.classifier == WebSocketListener::class) {
        return null
      }

      if (GenericMarshallers.canHandle(actualResponseType(type))) return null
      return PlainTextMarshaller
    }
  }
}
