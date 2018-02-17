package misk.web.marshal

import okhttp3.MediaType
import okio.BufferedSource
import kotlin.reflect.KType

/** Unmarshalls a typed object from an incoming source */
interface Unmarshaller {
  fun unmarshal(source: BufferedSource): Any?

  interface Factory {
    fun create(
        mediaType: MediaType,
        type: KType
    ): Unmarshaller?
  }
}
