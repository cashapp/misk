package misk.web.actions

import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.forwardRequestTo
import okhttp3.HttpUrl

class UpstreamResourceInterceptor(
  private val mappings: MutableList<out Mapping>
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
    val matchedMapping = findMappingMatch(chain) ?: return chain.proceed(chain.request)
    val proxyUrl = HttpUrl.parse(
        matchedMapping.upstreamBaseUrl.toString() + chain.request.url.encodedPath().removePrefix(
            matchedMapping.localPathPrefix))!!
    return chain.request.forwardRequestTo(proxyUrl)
  }

  private fun findMappingMatch(chain: NetworkChain): Mapping? {
    var matchedMapping: Mapping? = null
    for (mapping in mappings) {
      if (!chain.request.url.encodedPath().startsWith(mapping.localPathPrefix)) continue
      // TODO(adrw) remove maximal matching if we're doing non-overlapping first prefix match forwarding
      if (matchedMapping == null ||
          mapping.localPathPrefix.count { ch -> ch == '/' } >
          matchedMapping.localPathPrefix.count { ch -> ch == '/' }) {
            matchedMapping = mapping
          }
    }
    return matchedMapping
  }

//  TODO(adrw) fix this documentation if forwarding rewrites are restricted or other conditions in place
  /**
   * Maps URLs requested against this server to URLs of servers to delegate to
   *
   * localPathPrefix: `/_admin/`
   * upstreamBaseUrl: `http://localhost:3000/`
   *
   * An incoming request then for `/_admin/config.js` would route to `http://localhost:3000/config.js`.
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