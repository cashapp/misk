package misk.web.marshal

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KType
import misk.inject.typeLiteral
import misk.web.HttpCall
import misk.web.Response
import misk.web.ResponseBody
import okhttp3.MediaType

/** Marshalls typed kotlin objects into a [ResponseBody] */
interface Marshaller<in T> {
  /** @return the media type of the marshalled content, if known to the [Marshaller] */
  fun contentType(): MediaType?

  /** @return The object marshalled into a [ResponseBody] */
  fun responseBody(o: T): ResponseBody

  /**
   * This interface is used with Guice multibindings. Register instances by calling `multibind()` in a
   * `KAbstractModule`:
   * ```
   * multibind<Marshaller.Factory>().to<MyFactory>()
   * ```
   */
  interface Factory {
    fun create(mediaType: MediaType, type: KType): Marshaller<Any>?
  }

  /**
   * Alternate way to marshal the response body with access to the HttpCall. Invokes [responseBody] by default.
   *
   * @return The object marshalled into a [ResponseBody]
   */
  fun responseBody(o: T, httpCall: HttpCall): ResponseBody = responseBody(o)

  companion object {
    fun actualResponseType(type: KType): Type {
      val typeLiteral = type.typeLiteral()
      val javaType = when {
        typeLiteral.rawType == Response::class.java -> {
          (typeLiteral.type as ParameterizedType).actualTypeArguments[0]
        }
        else -> typeLiteral.type
      }
      // Unwrap wildcard types produced by Kotlin's declaration-site variance.
      // Response<out T> can produce "? extends T" instead of "T" for suspend function return types,
      // because KType.javaType reconstructs the type from Continuation metadata rather than from
      // Method.getGenericReturnType().
      return if (javaType is WildcardType) {
        javaType.upperBounds.firstOrNull() ?: javaType
      } else {
        javaType
      }
    }
  }
}
