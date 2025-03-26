package misk.web.extractors

import misk.Action
import misk.exceptions.BadRequestException
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.RequestHeader
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/** Binds parameters annotated [RequestHeader] to HTTP request headers. */
internal class RequestHeaderFeatureBinding private constructor(
  private val parameters: List<ParameterBinding>
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    for (element in parameters) {
      element.bind(subject)
    }
  }

  internal class ParameterBinding(
    val parameter: KParameter,
    private val converter: StringConverter,
    private val name: String,
    // private val throwOnDuplicate: Boolean todo("if a throwOnDuplicate flag is required")
  ) {
    fun bind(subject: Subject) {
      val requestHeaders = subject.httpCall.requestHeaders
      val rawValue = requestHeaders[name] ?:
      when {
        parameter.isOptional -> return
        parameter.type.isMarkedNullable -> return
        else -> throw BadRequestException("Required request header $name not present")
      }

      // todo(if required we could add a throwOnDuplicate flag)
      if (requestHeaders.values(name).size > 1) {
        throw BadRequestException("Multiple values found for [header=$name], consider using @misk.web.RequestHeaders instead")
      }

      val value = try {
        converter(rawValue)
      } catch (e: IllegalArgumentException) {
        throw BadRequestException("Invalid format for parameter: $name", e)
      }
      subject.setParameter(parameter, value)
    }
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      val bindings = action.parameters.mapNotNull { it.toRequestHeaderBinding() }
      if (bindings.isEmpty()) return null

      for (binding in bindings) {
        claimer.claimParameter(binding.parameter)
      }

      return RequestHeaderFeatureBinding(bindings)
    }

    private fun KParameter.toRequestHeaderBinding(): ParameterBinding? {
      val annotation = findAnnotation<RequestHeader>() ?: return null
      val name = annotation.value.ifBlank { name!! }

      val stringConverter = converterFor(type)
        ?: throw IllegalArgumentException("Unable to create converter for $name")

      return ParameterBinding(this, stringConverter, name)
    }
  }
}
