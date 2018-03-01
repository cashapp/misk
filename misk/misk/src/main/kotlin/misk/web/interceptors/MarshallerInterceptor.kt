package misk.web.interceptors

import misk.Action
import misk.NetworkChain
import misk.NetworkInterceptor
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.actions.WebSocketListener
import misk.web.marshal.GenericMarshallers
import misk.web.marshal.Marshaller
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

@Singleton
internal class MarshallerInterceptor constructor(private val marshaller: Marshaller<Any>) :
    NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
    @Suppress("UNCHECKED_CAST")
    val response = chain.proceed(chain.request) as Response<Any>

    val headers = marshaller.contentType()?.let {
      response.headers.newBuilder()
          .set("Content-Type", it.toString())
          .build()
    } ?: response.headers

    val body = marshaller.responseBody(response.body)
    return Response(body, headers, response.statusCode)
  }

  class Factory @Inject internal constructor(
    @JvmSuppressWildcards private val marshallerFactories: List<Marshaller.Factory>
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      if (action.returnType.classifier == WebSocketListener::class) {
        return null
      }
      return findMarshaller(action)?.let { MarshallerInterceptor(it) }
    }

    private fun findMarshaller(action: Action): Marshaller<Any>? {
      val responseMediaType = action.function.findAnnotation<ResponseContentType>()?.let {
        MediaType.parse(it.value)
      }

      if (responseMediaType == null || responseMediaType == MediaTypes.ALL_MEDIA_TYPE) {
        return genericMarshallerFor(responseMediaType, action.returnType)
      }

      val contentTypeMarshaller = marshallerFactories.mapNotNull {
        it.create(responseMediaType, action.returnType)
      }.firstOrNull()

      return contentTypeMarshaller
          ?: genericMarshallerFor(responseMediaType, action.returnType)
    }

    private fun genericMarshallerFor(mediaType: MediaType?, type: KType): Marshaller<Any> {
      return GenericMarshallers.from(mediaType, type) ?: throw IllegalArgumentException(
          "no marshaller for $mediaType as $type")
    }
  }
}
