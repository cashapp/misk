package misk.web.marshal

import misk.web.ResponseBody
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okhttp3.MediaType
import okio.Buffer
import okio.BufferedSink
import javax.inject.Inject
import kotlin.reflect.KType

class WebSocketMarshaller<R, in T>(val delegate: Unmarshaller<R>) : Marshaller<T> {
  override fun contentType(): MediaType? {
    return null
  }

  override fun responseBody(o: T): ResponseBody {
    val webSocketListener = o as WebSocketListener<R>

    val marshallingWebSocketListener = object : WebSocketListener<R>() {
      override fun onMessage(webSocket: WebSocket<R>, content: R) {
        val source = Buffer().writeUtf8(content.toString())
        val t = delegate.unmarshal(source)!!
        webSocketListener.onMessage(webSocket, t)
      }

      override fun onClosing(webSocket: WebSocket<R>, code: Int, reason: String?) {
        webSocketListener.onClosing(webSocket, code, reason)
      }

      override fun onClosed(webSocket: WebSocket<R>, code: Int, reason: String) {
        webSocketListener.onClosed(webSocket, code, reason)
      }

      override fun onFailure(webSocket: WebSocket<R>, t: Throwable) {
        webSocketListener.onFailure(webSocket, t)
      }
    }

    return object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
      }

      override fun get(): Any {
        return marshallingWebSocketListener
      }
    }
  }

  class Factory @Inject internal constructor(
    @JvmSuppressWildcards private val unmarshallerFactories: List<Unmarshaller.Factory>
  ) : Marshaller.Factory {
    override fun create(
      mediaType: MediaType,
      type: KType,
      factories: List<Marshaller.Factory>
    ): Marshaller<Any>? {
      // TODO(tso): the mediaType passed in here is the reverse of what we want, which
      // is fine since the response and request content types for websockets must be
      // equal for now.
      if (type.classifier != WebSocketListener::class) return null
      val webSocketType = type.arguments[0].type!!

      val unmarshaller =
          unmarshallerFactories.map { it.create<Any>(mediaType, webSocketType) }.firstOrNull()
              ?: GenericUnmarshallers.into(webSocketType)
              ?: throw IllegalArgumentException("no unmarshaller for $webSocketType")

      return WebSocketMarshaller(unmarshaller)
    }
  }
}
