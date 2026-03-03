package misk.web.marshal

import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSource
import kotlin.reflect.KType

/** Unmarshalls a typed object from an incoming source */
interface Unmarshaller {
  fun unmarshal(requestHeaders: Headers, source: BufferedSource): Any?

  /**
   * This interface is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<Unmarshaller.Factory>().to<MyFactory>()
   * ```
   */
  interface Factory {
    fun create(mediaType: MediaType, type: KType): Unmarshaller?
  }
}
