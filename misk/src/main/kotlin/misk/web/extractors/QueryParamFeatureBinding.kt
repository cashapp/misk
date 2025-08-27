package misk.web.extractors

import misk.Action
import misk.exceptions.BadRequestException
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.QueryParam
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/** Binds parameters annotated [QueryParam] to URL query parameters. */
internal class QueryParamFeatureBinding private constructor(
  private val parameters: List<ParameterBinding>
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    for (element in parameters) {
      element.bind(subject)
    }
  }

  /**
   * A query string parameter can be two things: a *primitive* or a List<*primitive*> This class
   * figures out which of the two is being represented based on the KParameter used, as well as
   * finding the KType of the primitive.
   */
  internal class ParameterBinding constructor(
    val parameter: KParameter,
    private val isList: Boolean,
    private val converter: StringConverter,
    private val name: String
  ) {
    fun bind(subject: Subject) {
      val values = subject.httpCall.url.queryParameterValues(name).map {
        it ?: throw IllegalArgumentException()
      }
      val value = parameterValue(values)
      subject.setParameter(parameter, value)
    }

    fun parameterValue(values: List<String>): Any? {
      // an empty list means the parameter was not present, if the parameter is optional or
      // nullable we can return null
      if (values.isEmpty() && (parameter.isOptional || parameter.type.isMarkedNullable)) return null
      // if the parameter is required (not optional, not nullable) then we need to have a value
      if (values.isEmpty()) {
        throw BadRequestException("Missing required query parameter: $name")
      }

      try {
        return if (isList) values.map { converter.convert(it) }
        else converter.convert(values.first())
      } catch (e: IllegalArgumentException) {
        throw BadRequestException("Invalid format for parameter: $name", e)
      }
    }
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer,
      stringConverterFactories: List<StringConverter.Factory>
    ): FeatureBinding? {
      val bindings = action.parameters.mapNotNull { it.toQueryBinding(stringConverterFactories) }
      if (bindings.isEmpty()) return null

      for (binding in bindings) {
        claimer.claimParameter(binding.parameter)
      }

      return QueryParamFeatureBinding(bindings)
    }

    internal fun KParameter.toQueryBinding(
      stringConverterFactories: List<StringConverter.Factory>,
    ): ParameterBinding? {
      val annotation = findAnnotation<QueryParam>() ?: return null
      val name = if (annotation.value.isBlank()) name!! else annotation.value

      val isList = type.classifier?.equals(List::class) ?: false
      val elementType = if (isList) type.arguments.first().type!! else type
      val stringConverter = converterFor(elementType, stringConverterFactories)
        ?: throw IllegalArgumentException("Unable to create converter for $name")

      return ParameterBinding(this, isList, stringConverter, name)
    }
  }
}
