package misk.web.extractors

import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType

/**
 * A query string parameter can be two things: a *primitive* or a List<*primitive*>
 *     This class figures out which of the two is being represented based on the KParameter
 *     used, as well as finding the KType of the primitive.
 */
internal class QueryStringParameterProcessor constructor(parameter: KParameter) {
  private val name: String? = parameter.name ?: parameter.type.javaType.typeName
  private val isList: Boolean = parameter.type.classifier?.equals(List::class) ?: false
  private val stringConverter = converterFor(
      if (isList) parameter.type.arguments.first().type!!
      else parameter.type
  ) ?: throw IllegalArgumentException("Unable to create converter for ${parameter.name}")

  fun extractFunctionArgumentValue(parameterStrings: List<String>): Any? {
    if (parameterStrings.isEmpty()) {
      return null
    }

    try {
      return if (isList) parameterStrings.map { stringConverter(it) }
      else stringConverter(parameterStrings.first())
    } catch (e: IllegalArgumentException) {
      throw IllegalArgumentException("Invalid format for parameter: $name", e)
    }
  }
}
