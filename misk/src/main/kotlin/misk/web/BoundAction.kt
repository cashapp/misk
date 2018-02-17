package misk.web

import misk.Interceptor
import misk.web.actions.WebAction
import misk.web.actions.asChain
import misk.web.extractors.ParameterExtractor
import misk.web.mediatype.MediaRange
import okhttp3.HttpUrl
import okhttp3.MediaType
import org.eclipse.jetty.http.HttpMethod
import java.util.regex.Matcher
import javax.inject.Provider
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

/**
 * Decodes an HTTP request into a call to a web action, then encodes its response into an HTTP
 * response.
 */
internal class BoundAction<A : WebAction, out R>(
    private val webActionProvider: Provider<A>,
    val interceptors: MutableList<Interceptor>,
    parameterExtractorFactories: List<ParameterExtractor.Factory>,
    val function: KFunction<R>,
    val pathPattern: PathPattern,
    private val httpMethod: HttpMethod,
    private val acceptedContentTypes: List<MediaRange>,
    private val responseContentType: MediaType?
) {
  private val parameterExtractors = function.parameters
      .drop(1) // the first parameter is always _this_
      .map { findParameterExtractor(parameterExtractorFactories, it) }

  private fun findParameterExtractor(
      parameterExtractorFactories: List<ParameterExtractor.Factory>,
      parameter: KParameter
  ): ParameterExtractor {
    var result: ParameterExtractor? = null
    for (factory in parameterExtractorFactories) {
      val parameterExtractor = factory.create(function, parameter, pathPattern) ?: continue
      if (result != null) {
        throw IllegalArgumentException(
            "multiple ways to extract $parameter: $result and $parameterExtractor"
        )
      }
      result = parameterExtractor
    }
    if (result == null) {
      throw IllegalArgumentException("no ways to extract $parameter")
    }
    return result
  }

  fun match(
      jettyRequest: HttpServletRequest,
      url: HttpUrl
  ): RequestMatch? {
    // Confirm the path and method matches
    val pathMatcher = pathMatcher(url) ?: return null
    if (jettyRequest.method != httpMethod.name) return null

    // Confirm the request content type matches the types we accept, and pick the most specific
    // content type match
    val requestContentType = jettyRequest.contentType?.let { MediaType.parse(it) }
    val requestContentTypeMatch =
        requestContentType?.closestMediaRangeMatch(acceptedContentTypes)
    if (requestContentType != null && requestContentTypeMatch == null) return null

    // Confirm we can generate a response content type matching the set accepted by the request,
    // and pick the most specific response content type match
    val responseContentTypeMatch =
        responseContentType?.closestMediaRangeMatch(jettyRequest.accepts())
    if (responseContentType != null && responseContentTypeMatch == null) return null

    return RequestMatch(this, pathMatcher, responseContentTypeMatch)
  }

  internal fun handle(
      request: Request,
      pathMather: Matcher
  ): Response<ResponseBody> {
    // Find values for all the parameters.
    val webAction = webActionProvider.get()
    val parameters = parameterExtractors.map {
      it.extract(webAction, request, pathMather)
    }

    val chain = webAction.asChain(function, parameters, *interceptors.toTypedArray())

    val response = chain.proceed(chain.args) as Response<*>

    if (response.body !is ResponseBody) {
      throw IllegalStateException("expected a ResponseBody for $webAction")
    }

    @Suppress("UNCHECKED_CAST")
    return response as Response<ResponseBody>
  }

  /** Returns a Matcher if requestUrl can be matched, else null */
  private fun pathMatcher(requestUrl: HttpUrl): Matcher? {
    val matcher = pathPattern.pattern.matcher(requestUrl.encodedPath())
    return if (matcher.matches()) matcher else null
  }
}

private fun HttpServletRequest.accepts(): List<MediaRange> {
  // TODO(mmihic): Don't blow up if one of the accept headers can't be parsed
  val accepts = getHeaders("Accept")?.toList()
      ?.flatMap {
        MediaRange.parseRanges(it)
      }

  return if (accepts == null || accepts.isEmpty()) {
    listOf(MediaRange.ALL_MEDIA)
  } else {
    accepts
  }
}

/** Matches a request to an action. Can be sorted to pick the most specific match amongst a set of candidates */
internal class RequestMatch(
    val action: BoundAction<*, *>,
    private val pathMatcher: Matcher,
    private val responseContentTypeMatch: MediaRange.Matcher?
) : Comparable<RequestMatch> {
  override fun compareTo(other: RequestMatch): Int {
    val patternDiff = action.pathPattern.compareTo(other.action.pathPattern)
    if (patternDiff != 0) return patternDiff

    if (responseContentTypeMatch != null && other.responseContentTypeMatch == null) return -1
    if (responseContentTypeMatch == null && other.responseContentTypeMatch != null) return 1
    if (responseContentTypeMatch != null && other.responseContentTypeMatch != null) {
      return responseContentTypeMatch.compareTo(other.responseContentTypeMatch)
    }

    return 0
  }

  fun handle(
      request: Request,
      jettyResponse: HttpServletResponse
  ) {
    val result = action.handle(request, pathMatcher)
    result.writeToJettyResponse(jettyResponse)
  }
}

private fun MediaType.closestMediaRangeMatch(ranges: List<MediaRange>) =
    ranges.mapNotNull { it.matcher(this) }.sorted().firstOrNull()
