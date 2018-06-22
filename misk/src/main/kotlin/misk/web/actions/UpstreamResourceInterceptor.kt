package misk.web.actions

import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.toMisk
import misk.web.toOkHttp3
import okhttp3.HttpUrl

class UpstreamResourceInterceptor(
  private val mappings: MutableList<out Mapping>
) : NetworkInterceptor {
  @Suppress("UNUSED_PARAMETER")
  override fun intercept(chain: NetworkChain): Response<*> {
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

    return chain.request.toOkHttp3(proxyUrl).toMisk()
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