package misk.web.extractors

import misk.enums.RawEnums
import misk.web.PathParam
import misk.web.PathPattern
import misk.web.Request
import misk.web.actions.WebAction
import java.lang.reflect.Type
import java.util.regex.Matcher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType

/**
 * Creates a [ParameterExtractor] that extracts the URL parameter with the same name as [parameter]
 * and returns it as a [String]. If the parameter name doesn't occur in [pathPattern], returns null.
 */
object PathPatternParameterExtractorFactory : ParameterExtractor.Factory {
  override fun create(
      function: KFunction<*>,
      parameter: KParameter,
      pathPattern: PathPattern
  ): ParameterExtractor? {
    val pathParamAnnotation = parameter.findAnnotation<PathParam>() ?: return null
    val parameterName =
        if (pathParamAnnotation.value.isBlank()) parameter.name
        else pathParamAnnotation.value

    val patternIndex = pathPattern.variableNames.indexOf(parameterName)
    if (patternIndex == -1) return null

    val parameterType = parameter.type.javaType
    val converter = converterFor(parameterType)
        ?: throw IllegalArgumentException(
            "cannot convert path parameters to ${parameterType.typeName}"
        )
    return object : ParameterExtractor {
      override fun extract(
          webAction: WebAction,
          request: Request,
          pathMatcher: Matcher
      ): Any? {
        val pathParam = pathMatcher.group(patternIndex + 1)
        return converter(pathParam)
      }
    }
  }
}

// TODO(mmihic): Pull this into a separate area where it can be used by QueryParam etc
private typealias StringConverter = (String) -> Any

private fun converterFor(type: Type): StringConverter? {
  if (type is Class<*> && type.isEnum) {
    return { param -> RawEnums.valueOf(type, param) }
  }

  return when (type) {
    String::class.java -> { param -> param }
    Int::class.java -> { param -> param.toInt() }
    Long::class.java -> { param -> param.toLong() }
    Double::class.java -> { param -> param.toDouble() }
    Float::class.java -> { param -> param.toFloat() }
    else -> null
  }
}
