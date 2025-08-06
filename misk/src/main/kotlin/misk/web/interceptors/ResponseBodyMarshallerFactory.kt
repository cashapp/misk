package misk.web.interceptors

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.web.marshal.GenericMarshallers
import misk.web.marshal.Marshaller
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

@Singleton
class ResponseBodyMarshallerFactory @Inject internal constructor(
  @JvmSuppressWildcards private val marshallerFactories: List<Marshaller.Factory>
) {
  /** Returns a marshaller for [action], or null if it has no response to marshal. */
  fun create(action: Action): Marshaller<Any> {
    val responseMediaType: MediaType? = action.responseContentType
    if (responseMediaType == null || responseMediaType == MediaTypes.ALL_MEDIA_TYPE) {
      return genericMarshallerFor(responseMediaType, action.returnType)
    }

    return create(responseMediaType,action.returnType)
  }

  fun create(responseMediaType: MediaType, type: KType): Marshaller<Any> {
    return marshallerFactories.firstNotNullOfOrNull {
      it.create(responseMediaType, type)
    } ?: genericMarshallerFor(responseMediaType, type)
  }

  private fun genericMarshallerFor(mediaType: MediaType?, type: KType): Marshaller<Any> {
    return GenericMarshallers.from(mediaType, type)
      ?: throw IllegalArgumentException("no marshaller for $mediaType as $type")
  }
}
