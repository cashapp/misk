package misk.web.extractors

import misk.web.PathPattern
import misk.web.QueryParam
import misk.web.Request
import misk.web.actions.WebAction
import java.util.regex.Matcher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/**
 * Creates a [ParameterExtractor] that extracts the Query String parameter with the same name as
 * [parameter] and returns it as whatever type is specified in [parameter]. Returns null if the
 * parameter doesn't occur in the request, or can't be parsed into the correct type.
 */
object QueryStringParameterExtractorFactory : ParameterExtractor.Factory {
  override fun create(
      function: KFunction<*>,
      parameter: KParameter,
      pathPattern: PathPattern
  ): ParameterExtractor? {
    val queryParamAnnotation = parameter.findAnnotation<QueryParam>() ?: return null
    val parameterName =
        if (queryParamAnnotation.value.isBlank()) parameter.name!!
        else queryParamAnnotation.value
    val queryParamProcessor = QueryStringParameterProcessor(parameter)

    return object : ParameterExtractor {
      override fun extract(
          webAction: WebAction,
          request: Request,
          pathMatcher: Matcher
      ): Any? {
        val parameterValues: List<String> = request.url.queryParameterValues(parameterName)
        return queryParamProcessor.extractFunctionArgumentValue(parameterValues)
      }
    }
  }

}
