package misk.web.extractors

import misk.web.PathPattern
import misk.web.Request
import misk.web.RequestHeaders
import misk.web.actions.WebAction
import java.util.regex.Matcher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/**
 * Creates a [ParameterExtractor] that returns [Request.headers] from a [Request] if the parameter
 * is annotated with [RequestHeaders].
 */
object HeadersParameterExtractorFactory : ParameterExtractor.Factory {
  private val extractor = object : ParameterExtractor {
    override fun extract(
        webAction: WebAction,
        request: Request,
        pathMatcher: Matcher
    ): Any? {
      return request.headers
    }
  }

  override fun create(
      function: KFunction<*>,
      parameter: KParameter,
      pathPattern: PathPattern
  ): ParameterExtractor? {
    if (parameter.findAnnotation<RequestHeaders>() == null) return null

    return extractor
  }
}
