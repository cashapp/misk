package misk.web.actions

import misk.logging.getLogger
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.toMisk
import misk.web.toOkHttp3
import okhttp3.HttpUrl

class UpstreamResourceInterceptor(
  private val mappings: MutableList<out Mapping>
) : NetworkInterceptor {
  internal val logger = getLogger<UpstreamResourceInterceptor>()

  @Suppress("UNUSED_PARAMETER")
  override fun intercept(chain: NetworkChain): Response<*> {
    // TODO: call the configured upstream (ie. webpack) server
    // if it 404s, call chain.proceed()
    // otherwise return the upstream's response

// request has path, find mapping that satisfies
//    if mapping found, make http request to target server
//    for that request, do fancy stuff to create new url
//    ie. drop everything below localprefix path, append to new
//    Then. make okhttp request with that path, same headers, same method, body
//    execute request, take response, route response back

//  if no mapping found, call chain.proceed()


//    methods misk request -> okhttp requst / misk response -> okhjttp response will be shared
//    put them in misk request/response. misk request.toOkHttp3 and okhttp3 response.toMiskResponse


//    bodies aren't the same. build sink from source by writing source to the sink
//    ignore web socket completely

// how to make adding mapping easy with new modules
//    Injection will do wiring
//    but... is rule going to be that when new service is built that redirection automatically happens on set paths

//    Extra challenge: deploying in production, bundle JS in JAR. In dev, JS served by webpack dev server
//    new service (ie. url shorteneer) consumes misk package as binary.


//    will have to inject/create okhttp3 client


    val requestSegments = chain.request.url.pathSegments()
    var matchedMapping: Mapping? = null

    for (mapping in this.mappings) {
      if (!pathSegmentsMatch(mapping.localPathPrefix.drop(1).dropLast(1).split('/'), requestSegments)) continue
      if (matchedMapping == null || mapping.localPathPrefix.count { ch -> ch == '/' } > matchedMapping.localPathPrefix.count { ch -> ch == '/' }) matchedMapping = mapping
    }

    if (matchedMapping == null) return chain.proceed(chain.request)

    val upstreamUrl = matchedMapping.upstreamBaseUrl
    val upstreamSegments = upstreamUrl.pathSegments()
    val upstreamPlusRequestSegments = upstreamSegments.subList(0, upstreamSegments.size - 1) + requestSegments.subList(
        upstreamSegments.size, requestSegments.size)

    val proxyUrl = HttpUrl.Builder()
        .scheme(upstreamUrl.scheme())
        .host(upstreamUrl.host())
        .port(upstreamUrl.port())
        .addPathSegments(upstreamPlusRequestSegments.joinToString("/"))
        .build()

    logger.info { requestSegments.toString() }
    logger.info { matchedMapping.toString() }
    logger.info { proxyUrl.toString() }

    val proxyResponse = chain.request.toOkHttp3(proxyUrl)
    return proxyResponse.toMisk()
  }

  private fun pathSegmentsMatch(
    localPathSegments: List<String>,
    requestSegments: MutableList<String>
  ) : Boolean {
    for ((i, localPathSegment) in localPathSegments.withIndex()) {
      if (i > requestSegments.size - 1) break
      if (requestSegments[i] != localPathSegment) return false
    }
    return true
  }

  /**
   * Imagine that we had the following Mapping:
   *
   * localPathPrefix: `/_admin/`
   * upstreamBaseUrl: `http://localhost:3000/`
   *
   * An incoming request for `/_admin/config.js` would route to `http://localhost:3000/config.js`.
   */
  data class Mapping(
    val localPathPrefix: String,
    val upstreamBaseUrl: HttpUrl
  ) {
    init {
      require(localPathPrefix.endsWith("/") &&
          localPathPrefix.startsWith("/") &&
          upstreamBaseUrl.encodedPath().endsWith("/"))
    }
  }
}