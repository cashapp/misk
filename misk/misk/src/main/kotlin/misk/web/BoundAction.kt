package misk.web

import misk.Action
import misk.ApplicationInterceptor
import misk.security.authz.AccessInterceptor
import misk.web.actions.WebAction
import misk.web.actions.WebActionMetadata
import misk.web.actions.WebSocketListener
import misk.web.actions.asChain
import misk.web.extractors.ParameterExtractor
import misk.web.marshal.Marshaller
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.compareTo
import okhttp3.HttpUrl
import okhttp3.MediaType
import java.util.regex.Matcher
import javax.inject.Provider
import kotlin.reflect.KParameter

/**
 * Decodes an HTTP request into a call to a web action, then encodes its response into an HTTP
 * response.
 */
internal class BoundAction<A : WebAction>(
  private val webActionProvider: Provider<A>,
  private val networkInterceptors: List<NetworkInterceptor>,
  private val applicationInterceptors: List<ApplicationInterceptor>,
  private val responseBodyMarshaller: Marshaller<Any>?,
  parameterExtractorFactories: List<ParameterExtractor.Factory>,
  val pathPattern: PathPattern,
  val action: Action,
  val dispatchMechanism: DispatchMechanism
) {
  private val parameterExtractors = action.function.parameters
      .drop(1) // the first parameter is always _this_
      .map { findParameterExtractor(parameterExtractorFactories, it) }

  private fun findParameterExtractor(
    parameterExtractorFactories: List<ParameterExtractor.Factory>,
    parameter: KParameter
  ): ParameterExtractor {
    val results = parameterExtractorFactories.mapNotNull {
      it.create(action.function, parameter, pathPattern)
    }
    require(results.size == 1) {
      "expected exactly 1 way to extract $parameter but was $results"
    }
    return results[0]
  }

  fun match(
    requestDispatchMechanism: DispatchMechanism?,
    requestContentType: MediaType?,
    requestAcceptedTypes: List<MediaRange>,
    url: HttpUrl
  ): BoundActionMatch? {
    // Confirm the path and method matches
    val pathMatcher = pathPattern.matcher(url) ?: return null
    if (requestDispatchMechanism != dispatchMechanism) return null

    // Confirm the request content type matches the types we accept, and pick the most specific
    // content type match
    val requestContentTypeMatch =
        requestContentType?.closestMediaRangeMatch(action.acceptedMediaRanges)
    if (requestContentType != null && requestContentTypeMatch == null) return null

    // Confirm we can generate a response content type matching the set accepted by the request,
    // and pick the most specific response content type match
    val responseContentTypeMatch =
        action.responseContentType?.closestMediaRangeMatch(requestAcceptedTypes)
    if (action.responseContentType != null && responseContentTypeMatch == null) return null

    val acceptedMediaRange = requestContentTypeMatch?.mediaRange ?: MediaRange.ALL_MEDIA
    val requestCharsetMatch = requestContentTypeMatch?.matchesCharset ?: false

    return BoundActionMatch(
        this,
        pathMatcher,
        acceptedMediaRange,
        requestCharsetMatch,
        action.responseContentType ?: MediaTypes.ALL_MEDIA_TYPE
    )
  }

  internal fun handle(httpCall: HttpCall, pathMatcher: Matcher) {
    // Find values for all the parameters.
    val webAction = webActionProvider.get()

    // RequestBridgeInterceptor necessarily needs to be the last NetworkInterceptor run.
    val interceptors = networkInterceptors.toMutableList()
    interceptors.add(RequestBridgeInterceptor(
        parameterExtractors, responseBodyMarshaller!!, applicationInterceptors, pathMatcher))

    // Format the response for gRPC.
    if (dispatchMechanism == DispatchMechanism.GRPC) {
      // Add the required gRPC trailers if that's the mechanism.
      // TODO(jwilson): permit non-0 GRPC statuses.
      httpCall.requireTrailers()
      httpCall.setResponseTrailer("grpc-status", "0")
      // TODO(jwilson): permit non-identity GRPC encoding.
      httpCall.setResponseHeader("grpc-encoding", "identity")
      httpCall.setResponseHeader("grpc-accept-encoding", "gzip")
    }

    val chain = RealNetworkChain(action, webAction, httpCall, interceptors.toList())
    chain.proceed(httpCall)
  }

  internal fun handleWebSocket(
    httpCall: HttpCall,
    pathMatcher: Matcher
  ): WebSocketListener {
    val webAction = webActionProvider.get()
    val parameters = parameterExtractors.map {
      it.extract(webAction, httpCall, pathMatcher)
    }

    val chain = webAction.asChain(action.function, parameters, listOf())
    return chain.proceed(chain.args) as WebSocketListener
  }

  internal val metadata: WebActionMetadata by lazy {
    WebActionMetadata(
        name = action.name,
        function = action.function,
        functionAnnotations = action.function.annotations,
        acceptedMediaRanges = action.acceptedMediaRanges,
        responseContentType = action.responseContentType,
        parameterTypes = action.parameterTypes,
        requestType = action.requestType,
        returnType = action.returnType,
        pathPattern = pathPattern,
        applicationInterceptors = applicationInterceptors,
        networkInterceptors = networkInterceptors,
        dispatchMechanism = dispatchMechanism,
        allowedServices = fetchAllowedCallers(
            applicationInterceptors, AccessInterceptor::allowedServices),
        allowedRoles = fetchAllowedCallers(applicationInterceptors, AccessInterceptor::allowedRoles)
    )
  }

  private fun fetchAllowedCallers(
    applicationInterceptors: List<ApplicationInterceptor>,
    allowedCallersFun: (AccessInterceptor) -> Set<String>
  ): Set<String> {
    for (interceptor in applicationInterceptors) {
      if (interceptor is AccessInterceptor) {
        return allowedCallersFun.invoke(interceptor)
      }
    }
    return setOf()
  }
}

/** Matches a request. Can be sorted to pick the most specific match amongst a set of candidates. */
internal open class RequestMatch(
  private val pathPattern: PathPattern,
  private val acceptedMediaRange: MediaRange,
  private val requestCharsetMatch: Boolean,
  private val responseContentType: MediaType
) : Comparable<RequestMatch> {

  override fun compareTo(other: RequestMatch): Int {
    // More specific path pattern comes first.
    val patternDiff = pathPattern.compareTo(other.pathPattern)
    if (patternDiff != 0) return patternDiff

    // More specific request content type comes first.
    val requestContentTypeDiff = acceptedMediaRange.compareTo(other.acceptedMediaRange)
    if (requestContentTypeDiff != 0) return requestContentTypeDiff

    // More specific response content type comes first.
    val responseContentTypeDiff = responseContentType.compareTo(other.responseContentType)
    if (responseContentTypeDiff != 0) return responseContentTypeDiff

    // Matching charset comes first.
    val requestCharsetMatchDiff = -requestCharsetMatch.compareTo(other.requestCharsetMatch)
    if (requestCharsetMatchDiff != 0) return requestCharsetMatchDiff

    return 0
  }

  override fun toString(): String =
      "path: $pathPattern, accepts: $acceptedMediaRange, emits: $responseContentType"
}

/** A [RequestMatch] associated with the action that matched. */
internal class BoundActionMatch(
  val action: BoundAction<*>,
  private val pathMatcher: Matcher,
  acceptedMediaRange: MediaRange,
  requestCharsetMatch: Boolean,
  responseContentType: MediaType
) : RequestMatch(action.pathPattern, acceptedMediaRange, requestCharsetMatch, responseContentType) {

  /** Handles [httpCall] by handing it off to the action. */
  fun handle(httpCall: HttpCall) {
    action.handle(httpCall, pathMatcher)
  }

  fun handleWebSocket(httpCall: HttpCall): WebSocketListener {
    return action.handleWebSocket(httpCall, pathMatcher)
  }
}

private fun MediaType.closestMediaRangeMatch(ranges: List<MediaRange>) =
    ranges.mapNotNull { it.matcher(this) }.sorted().firstOrNull()

/**
 * Acts as the bridge between network interceptors and application interceptors.
 *
 * This expects the application chain to return a value or a Response wrapping a value.
 * If it does, this will be written to the HTTP call's response.
 */
private class RequestBridgeInterceptor(
  val parameterExtractors: List<ParameterExtractor>,
  val responseBodyMarshaller: Marshaller<Any>,
  val applicationInterceptors: List<ApplicationInterceptor>,
  val pathMatcher: Matcher
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val httpCall = chain.httpCall
    val arguments = parameterExtractors.map {
      it.extract(chain.webAction, httpCall, pathMatcher)
    }

    val applicationChain = chain.webAction.asChain(
        chain.action.function, arguments, applicationInterceptors)

    var returnValue = applicationChain.proceed(applicationChain.args)

    // If the return value is a boxed response, emit its status and headers.
    if (returnValue is Response<*>) {
      httpCall.statusCode = returnValue.statusCode
      httpCall.addResponseHeaders(returnValue.headers)
      returnValue = returnValue.body!!
    }

    // If the response body needs to be written, write it.
    httpCall.takeResponseBody()?.use { sink ->
      val contentType = responseBodyMarshaller.contentType()
      if (httpCall.responseHeaders.get("Content-Type") == null && contentType != null) {
        httpCall.setResponseHeader("Content-Type", contentType.toString())
      }

      val responseBody = responseBodyMarshaller.responseBody(returnValue)
      responseBody.writeTo(sink)
    }
  }
}
