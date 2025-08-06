package misk.web.sse

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.ResponseBody
import misk.web.marshal.Marshaller
import misk.web.marshal.Unmarshaller
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSource
import kotlin.reflect.KType

const val COLON: String = ":"

const val SPACE: String = " "

const val END_OF_LINE: String = "\r\n"

val END_OF_LINE_VARIANTS: Regex = Regex("\r\n|\r|\n")

object ServerSentEventMarshaller : Marshaller<Any> {
  override fun contentType() = MediaTypes.SERVER_EVENT_STREAM_TYPE

  override fun responseBody(o: Any): ResponseBody {
    val event = o as ServerSentEvent
    val encodedEvent = event.encodeToString() + END_OF_LINE
    return encodedEvent.toResponseBody()
  }

  private fun ServerSentEvent.encodeToString() =
    buildString {
      appendField("event", event)
      appendField("data", data)
      appendField("id", id)
      appendField("retry", retry)
      appendField("", comments)
    }

  private fun <T> StringBuilder.appendField(name: String, value: T?) {
    value?.toString()
      ?.split(END_OF_LINE_VARIANTS)
      ?.forEach { append("$name$COLON$SPACE$it$END_OF_LINE") }
  }

  @Singleton
  class Factory @Inject constructor() : Marshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? {
      if (mediaType != MediaTypes.SERVER_EVENT_STREAM_TYPE) {
        return null
      }
      if (type.classifier != ServerSentEvent::class) {
        return null
      }

      return ServerSentEventMarshaller
    }
  }
}

object ServerSentEventUnmarshaller : Unmarshaller {

  override fun unmarshal(requestHeaders: Headers, source: BufferedSource): Any? {
    var data: MutableList<String>? = null
    var event: String? = null
    var id: String? = null
    var retry: Long? = null
    var comments: MutableList<String>? = null

    while (!source.exhausted()) {
      val line = source.readUtf8Line() ?: break

      // Empty line signals end of event
      if (line.isEmpty()) {
        break
      }

      val colonIndex = line.indexOf(COLON)
      if (colonIndex == -1) {
        // Line without colon is ignored per SSE spec
        continue
      }

      val field = line.substring(0, colonIndex)
      var value = line.substring(colonIndex + COLON.length)

      // Remove optional leading space
      if (value.startsWith(SPACE)) {
        value = value.substring(1)
      }

      when (field) {
        "data" -> {
          if (data == null) data = mutableListOf()
          data.add(value)
        }

        "event" -> event = value
        "id" -> id = value
        "retry" -> retry = value.toLongOrNull()
        "" -> {
          if (comments == null) comments = mutableListOf()
          comments.add(value)
        }
        // Unknown fields are ignored per SSE spec
      }
    }

    return ServerSentEvent(
      data = data?.joinToString(System.lineSeparator()),
      event = event,
      id = id,
      retry = retry,
      comments = comments?.joinToString(System.lineSeparator()),
    )
  }

  @Singleton
  class Factory @Inject constructor() : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType != MediaTypes.SERVER_EVENT_STREAM_TYPE) {
        return null
      }

      if (type.classifier != ServerSentEvent::class) {
        return null
      }

      return ServerSentEventUnmarshaller
    }
  }
}
