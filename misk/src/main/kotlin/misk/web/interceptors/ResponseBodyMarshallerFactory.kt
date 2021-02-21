package misk.web.interceptors

import misk.Action
import misk.web.marshal.GenericMarshallers
import misk.web.marshal.Marshaller
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType

@Singleton
class ResponseBodyMarshallerFactory @Inject internal constructor(
  @JvmSuppressWildcards private val marshallerFactories: List<Marshaller.Factory>
) {
  /** Returns a marshaller for [action], or null if it has no response to marshal. */
  fun create(action: Action): Marshaller<Any> {
    val responseMediaType = action.responseContentType
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
    return GenericMarshallers.from(mediaType, type)
      ?: throw IllegalArgumentException("no marshaller for $mediaType as $type")
  }
}
