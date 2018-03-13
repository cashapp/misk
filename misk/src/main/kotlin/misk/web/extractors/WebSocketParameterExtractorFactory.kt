package misk.web.extractors

import misk.web.PathPattern
import misk.web.Request
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.marshal.GenericMarshallers
import misk.web.marshal.Marshaller
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import okhttp3.MediaType
import okio.Buffer
import java.util.regex.Matcher
import javax.inject.Inject
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

class WebSocketParameterExtractor(private val marshaller: Marshaller<Any>) : ParameterExtractor {
  override fun extract(
    webAction: WebAction,
    request: Request,
    pathMatcher: Matcher
  ): Any? {
    val ws = request.websocket!!
    request.websocket = object : WebSocket<Any> {
      override fun queueSize(): Long {
        return ws.queueSize()
      }

      override fun send(content: Any): Boolean {
        val buffer = Buffer()
        marshaller.responseBody(content).writeTo(buffer)
        val utf8 = buffer.readUtf8()
        return ws.send(utf8)
      }

      override fun close(code: Int, reason: String?): Boolean {
        return ws.close(code, reason)
      }

      override fun cancel() {
        ws.cancel()
      }
    }

    return request.websocket
  }

  class Factory @Inject internal constructor(
    @JvmSuppressWildcards private val marshallerFactories: List<Marshaller.Factory>
  ) : ParameterExtractor.Factory {
    override fun create(
      function: KFunction<*>,
      parameter: KParameter,
      pathPattern: PathPattern
    ): ParameterExtractor? {
      if (parameter.type.classifier != WebSocket::class) return null

      val responseMediaType =
          function.findAnnotation<ResponseContentType>()?.value?.asMediaType()
              ?: MediaTypes.TEXT_PLAIN_UTF8.asMediaType()

      val webSocketType = parameter.type.arguments[0].type!!

      val marshaller = marshallerFactories.mapNotNull {
        it.create(responseMediaType, webSocketType, marshallerFactories)
      }.firstOrNull() ?: genericMarshallerFor(responseMediaType, webSocketType)

      return WebSocketParameterExtractor(marshaller)
    }

    private fun genericMarshallerFor(mediaType: MediaType?, type: KType): Marshaller<Any> {
      return GenericMarshallers.from(mediaType, type) ?: throw IllegalArgumentException(
          "no marshaller for $mediaType as $type")
    }
  }
}
