package misk.web.extractors

import misk.web.PathPattern
import misk.web.Request
import misk.web.actions.WebAction
import java.util.regex.Matcher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

interface ParameterExtractor {
  /**
   * Extracts a parameter from [request], such as a URL parameter or the request body.
   */
  fun extract(
      webAction: WebAction,
      request: Request,
      pathMatcher: Matcher
  ): Any?

  interface Factory {
    /**
     * Returns an instance of a [ParameterExtractor] that extracts the value for [parameter],
     * or null if the extractor does not apply to [parameter].
     *
     * See [HeadersParameterExtractorFactory] and [RequestBodyParameterExtractor] for
     * examples.
     */
    fun create(
        function: KFunction<*>,
        parameter: KParameter,
        pathPattern: PathPattern
    ): ParameterExtractor?
  }
}
