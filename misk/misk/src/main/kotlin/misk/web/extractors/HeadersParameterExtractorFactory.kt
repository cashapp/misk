package misk.web.extractors

import misk.web.PathPattern
import misk.web.HttpCall
import misk.web.RequestHeaders
import misk.web.actions.WebAction
import java.util.regex.Matcher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/**
 * Creates a [ParameterExtractor] that returns [HttpCall.requestHeaders] from a [HttpCall] if the
 * parameter is annotated with [RequestHeaders].
 */
object HeadersParameterExtractorFactory : ParameterExtractor.Factory {
  private val extractor = object : ParameterExtractor {
    override fun extract(
      webAction: WebAction,
      httpCall: HttpCall,
      pathMatcher: Matcher
    ): Any? {
      return httpCall.requestHeaders
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
