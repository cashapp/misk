package misk.web.marshal

import kotlin.reflect.KType
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSource

/** Unmarshalls a typed object from an incoming source */
interface Unmarshaller {
  fun unmarshal(requestHeaders: Headers, source: BufferedSource): Any?

  /**
   * This interface is used with Guice multibindings. Register instances by calling `multibind()` in a
   * `KAbstractModule`:
   * ```
   * multibind<Unmarshaller.Factory>().to<MyFactory>()
   * ```
   */
  interface Factory {
    fun create(mediaType: MediaType, type: KType): Unmarshaller?
  }
}
