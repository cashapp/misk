package misk.web

import misk.Interceptor
import misk.web.actions.WebAction
import misk.web.actions.asChain
import misk.web.extractors.ParameterExtractor
import okhttp3.Headers
import okhttp3.HttpUrl
import okio.Okio
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
internal class BoundAction<A : WebAction, R>(
  val webActionProvider: Provider<A>,
  val interceptors: MutableList<Interceptor>,
  parameterExtractorFactories: List<ParameterExtractor.Factory>,
  val function: KFunction<R>,
  val pathPattern: PathPattern,
  val httpMethod: HttpMethod
) {
  val parameterExtractors = ArrayList<ParameterExtractor>()

  init {
    for (parameter in function.parameters) {
      // This first parameter is always this.
      if (parameter.index == 0) {
        continue
      }

      parameterExtractors.add(findParameterExtractor(parameterExtractorFactories, parameter))
    }
  }

  private fun findParameterExtractor(
    parameterExtractorFactories: List<ParameterExtractor.Factory>,
    parameter: KParameter
  ): ParameterExtractor {
    var result: ParameterExtractor? = null
    for (factory in parameterExtractorFactories) {
      val parameterExtractor = factory.create(parameter, pathPattern) ?: continue
      if (result != null) {
        throw IllegalArgumentException("multiple ways to extract $parameter: $result and $parameterExtractor")
      }
      result = parameterExtractor
    }
    if (result == null) {
      throw IllegalArgumentException("no ways to extract $parameter")
    }
    return result
  }

  /** Returns true if this bound action handled the request. */
  fun tryHandle(
    jettyRequest: HttpServletRequest,
    jettyResponse: HttpServletResponse
  ): Boolean {
    val pathMatcher = matcher(jettyRequest.httpUrl()) ?: return false

    if (jettyRequest.method != httpMethod.name) return false

    val result = handle(jettyRequest.asRequest(), pathMatcher)
    result.writeToJettyResponse(jettyResponse)

    return true
  }

  private fun handle(
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
  private fun matcher(requestUrl: HttpUrl): Matcher? {
    val matcher = pathPattern.pattern.matcher(requestUrl.encodedPath())
    return if (matcher.matches()) matcher else null
  }
}

private fun HttpServletRequest.headers(): Headers {
  val headersBuilder = Headers.Builder()
  val headerNames = headerNames
  for (headerName in headerNames) {
    val headerValues = getHeaders(headerName)
    for (headerValue in headerValues) {
      headersBuilder.add(headerName, headerValue)
    }
  }
  return headersBuilder.build()
}

private fun HttpServletRequest.httpUrl(): HttpUrl {
  val urlString = requestURL.toString() +
      if (queryString != null) "?" + queryString
      else ""
  return HttpUrl.parse(urlString)!!
}

private fun HttpServletRequest.httpMethod() = HttpMethod.valueOf(method)

private fun HttpServletRequest.bufferedSource() = Okio.buffer(Okio.source(inputStream))

private fun HttpServletRequest.asRequest() = Request(
    httpUrl(),
    httpMethod(),
    headers(),
    bufferedSource()
)
