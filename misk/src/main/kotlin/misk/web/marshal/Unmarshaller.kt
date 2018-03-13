package misk.web.marshal

import okhttp3.MediaType
import okio.BufferedSource
import kotlin.reflect.KType

/** Unmarshalls a typed object from an incoming source */
interface Unmarshaller<out T> {
  fun unmarshal(source: BufferedSource): T?

  interface Factory {
    fun <T> create(mediaType: MediaType, type: KType): Unmarshaller<T>?
  }
}
