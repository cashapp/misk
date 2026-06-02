package misk.web.extractors

import javax.servlet.http.Cookie
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import misk.Action
import misk.exceptions.BadRequestException
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.RequestCookie

/** Binds parameters annotated [RequestCookie] to HTTP request cookies. */
internal class RequestCookieFeatureBinding private constructor(private val parameters: List<ParameterBinding>) :
  FeatureBinding {
  override fun beforeCall(subject: Subject) {
    for (element in parameters) {
      element.bind(subject)
    }
  }

  internal class ParameterBinding(
    val parameter: KParameter,
    private val converter: (Cookie) -> Any?,
    private val name: String,
  ) {
    fun bind(subject: Subject) {
      val matchingCookies = subject.httpCall.cookies.filter { it.name == name }

      if (matchingCookies.size > 1) {
        throw BadRequestException(
          "Multiple values found for [cookie=$name], consider using @misk.web.RequestCookies instead"
        )
      }

      val cookie =
        matchingCookies.firstOrNull()
          ?: when {
            parameter.isOptional -> return
            parameter.type.isMarkedNullable -> return
            else -> throw BadRequestException("Required request cookie $name not present")
          }

      val value =
        try {
          converter(cookie)
        } catch (e: IllegalArgumentException) {
          throw BadRequestException("Invalid format for parameter: $name", e)
        }
      subject.setParameter(parameter, value)
    }
  }

  companion object Factory : FeatureBinding.Factory {
    private val cookieType: KType = Cookie::class.createType(nullable = false)

    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer,
      stringConverterFactories: List<StringConverter.Factory>,
    ): FeatureBinding? {
      val bindings = action.parameters.mapNotNull { it.toRequestCookieBinding(stringConverterFactories) }
      if (bindings.isEmpty()) return null

      for (binding in bindings) {
        claimer.claimParameter(binding.parameter)
      }

      return RequestCookieFeatureBinding(bindings)
    }

    private fun KParameter.toRequestCookieBinding(
      stringConverterFactories: List<StringConverter.Factory>
    ): ParameterBinding? {
      val annotation = findAnnotation<RequestCookie>() ?: return null
      val name = annotation.value.ifBlank { name!! }

      val cookieConverter: (Cookie) -> Any? =
        when {
          type.isSubtypeOf(cookieType) -> ({ it })

          else -> {
            val stringConverter =
              converterFor(type, stringConverterFactories)
                ?: throw IllegalArgumentException("Unable to create converter for $name")

            ({ cookie -> stringConverter.convert(cookie.value) })
          }
        }

      return ParameterBinding(this, cookieConverter, name)
    }
  }
}
