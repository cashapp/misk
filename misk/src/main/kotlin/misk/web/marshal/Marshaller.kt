package misk.web.marshal

import misk.inject.typeLiteral
import misk.web.HttpCall
import misk.web.Response
import misk.web.ResponseBody
import okhttp3.MediaType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KType

/** Marshalls typed kotlin objects into a [ResponseBody] */
interface Marshaller<in T> {
  /** @return the media type of the marshalled content, if known to the [Marshaller] */
  fun contentType(): MediaType?

  /** @return The object marshalled into a [ResponseBody] */
  fun responseBody(o: T): ResponseBody

  /**
   * This interface is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<Marshaller.Factory>().to<MyFactory>()
   * ```
   */
  interface Factory {
    fun create(mediaType: MediaType, type: KType): Marshaller<Any>?
  }

  /**
   * Alternate way to marshal the response body with access to the HttpCall.
   * Invokes [responseBody] by default.
   *
   * @return The object marshalled into a [ResponseBody]
   */
  fun responseBody(o: T, httpCall: HttpCall): ResponseBody = responseBody(o)

  companion object {
    fun actualResponseType(type: KType): Type {
      val typeLiteral = type.typeLiteral()
      return when {
        typeLiteral.rawType == Response::class.java -> {
          (typeLiteral.type as ParameterizedType).actualTypeArguments[0]
        }
        else -> typeLiteral.type
      }
    }
  }
}
