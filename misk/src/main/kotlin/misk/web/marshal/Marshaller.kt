package misk.web.marshal

import misk.inject.typeLiteral
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

  interface Factory {
    fun create(
        mediaType: MediaType,
        type: KType
    ): Marshaller<Any>?
  }

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
