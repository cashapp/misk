package misk.web

import com.google.inject.Key
import misk.Action
import misk.ApplicationInterceptor
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.scope.SeedDataTransformer
import misk.security.authz.AccessInterceptor
import misk.web.actions.WebAction
import misk.web.actions.asChain
import misk.web.actions.findAnnotationWithOverrides
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.compareTo
import misk.web.metadata.webaction.WebActionMetadata
import okhttp3.HttpUrl
import okhttp3.MediaType
import java.util.regex.Matcher
import javax.inject.Provider
import javax.servlet.http.HttpServletRequest
import kotlin.reflect.KType

/**
 * Decodes an HTTP request into a call to a web action, then encodes its response into an HTTP
 * response.
 */
internal class BoundAction<A : WebAction>(
  private val scope: ActionScope,
  private val webActionProvider: Provider<A>,
  private val networkInterceptors: List<NetworkInterceptor>,
  private val applicationInterceptors: List<ApplicationInterceptor>,
  private val webActionBinding: WebActionBinding,
  private val seedDataTransformers: List<SeedDataTransformer>,
  val pathPattern: PathPattern,
  val action: Action,
) {

  fun match(
    requestDispatchMechanism: DispatchMechanism?,
    requestContentType: MediaType?,
    requestAcceptedTypes: List<MediaRange>,
    url: HttpUrl
  ): BoundActionMatch? {
    // Confirm the path and method matches
    val pathMatcher = pathPattern.matcher(url) ?: return null
    if (requestDispatchMechanism != action.dispatchMechanism) return null

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
      action = this,
      pathMatcher = pathMatcher,
      acceptedMediaRange = acceptedMediaRange,
      requestCharsetMatch = requestCharsetMatch,
      responseContentType = action.responseContentType ?: MediaTypes.ALL_MEDIA_TYPE
    )
  }

  fun matchByUrl(url: HttpUrl): BoundActionMatch? {
    val patchMather = pathPattern.matcher(url) ?: return null
    return BoundActionMatch(
      action = this,
      pathMatcher = patchMather,
      acceptedMediaRange = MediaRange.ALL_MEDIA,
      requestCharsetMatch = false,
      responseContentType = MediaTypes.ALL_MEDIA_TYPE
    )
  }

  /**
   * Returns true if this [BoundAction] has identical routing annotations as the provided
   * [BoundAction].
   */
  fun hasIdenticalRouting(other: BoundAction<*>): Boolean {
    if (pathPattern.pattern != other.pathPattern.pattern) {
      return false
    }
    if (action.dispatchMechanism != other.action.dispatchMechanism) {
      return false
    }
    if (action.acceptedMediaRanges != other.action.acceptedMediaRanges) {
      return false
    }
    if (action.responseContentType != other.action.responseContentType) {
      return false
    }
    return true
  }

  internal fun scopeAndHandle(
    request: HttpServletRequest,
    httpCall: HttpCall,
    pathMatcher: Matcher
  ) {
    val initialSeedData = mapOf<Key<*>, Any?>(
      keyOf<HttpServletRequest>() to request,
      keyOf<HttpCall>() to httpCall,
    )
    val seedData =
      seedDataTransformers.fold(initialSeedData) { seedData, interceptor ->
        interceptor.transform(seedData)
      }
    scope.enter(seedData).use {
      handle(httpCall, pathMatcher)
    }
  }

  private fun handle(httpCall: HttpCall, pathMatcher: Matcher) {
    // Find values for all the parameters.
    val webAction = webActionProvider.get()

    // RequestBridgeInterceptor necessarily needs to be the last NetworkInterceptor run.
    val interceptors = networkInterceptors.toMutableList()
    interceptors.add(
      RequestBridgeInterceptor(
        webActionBinding, applicationInterceptors, pathMatcher
      )
    )

    val chain = RealNetworkChain(action, webAction, httpCall, interceptors.toList())
    chain.proceed(httpCall)
  }

  internal val metadata: WebActionMetadata by lazy {
    WebActionMetadata(
      name = action.name,
      function = action.function,
      description = action.function.findAnnotationWithOverrides<Description>()?.text,
      functionAnnotations = action.function.annotations,
      acceptedMediaRanges = action.acceptedMediaRanges,
      responseContentType = action.responseContentType,
      parameterTypes = action.parameterTypes,
      parameters = action.parameters,
      requestType = action.requestType,
      returnType = action.returnType,
      responseType = determineResponseType(action.returnType),
      pathPattern = pathPattern,
      applicationInterceptors = applicationInterceptors,
      networkInterceptors = networkInterceptors,
      dispatchMechanism = action.dispatchMechanism,
      allowedServices = fetchAllowedCallers(
        applicationInterceptors, AccessInterceptor::allowedServices
      ),
      allowedCapabilities = fetchAllowedCallers(
        applicationInterceptors,
        AccessInterceptor::allowedCapabilities
      )
    )
  }

  private fun determineResponseType(returnType: KType): KType? {
    if (returnType.arguments.isNotEmpty()) {
      return returnType.arguments[0].type
    }
    return null
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

  override fun toString() = "BoundAction[$action]"
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
  val pathMatcher: Matcher,
  acceptedMediaRange: MediaRange,
  requestCharsetMatch: Boolean,
  responseContentType: MediaType
) : RequestMatch(action.pathPattern, acceptedMediaRange, requestCharsetMatch, responseContentType)

private fun MediaType.closestMediaRangeMatch(ranges: List<MediaRange>) =
  ranges.mapNotNull { it.matcher(this) }.sorted().firstOrNull()

/**
 * Acts as the bridge between network interceptors and application interceptors.
 *
 * This expects the application chain to return a value or a Response wrapping a value.
 * If it does, this will be written to the HTTP call's response.
 */
private class RequestBridgeInterceptor(
  val webActionBinding: WebActionBinding,
  val applicationInterceptors: List<ApplicationInterceptor>,
  val pathMatcher: Matcher
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val httpCall = chain.httpCall
    val arguments = webActionBinding.beforeCall(chain.webAction, httpCall, pathMatcher)

    val applicationChain = chain.webAction.asChain(
      chain.action.function, arguments, applicationInterceptors, httpCall
    )

    var returnValue = applicationChain.proceed(applicationChain.args)

    // If the return value is a boxed response, emit its status and headers.
    if (returnValue is Response<*>) {
      httpCall.statusCode = returnValue.statusCode
      httpCall.addResponseHeaders(returnValue.headers)
      returnValue = returnValue.body!!
    }

    webActionBinding.afterCall(chain.webAction, httpCall, pathMatcher, returnValue)
  }
}
