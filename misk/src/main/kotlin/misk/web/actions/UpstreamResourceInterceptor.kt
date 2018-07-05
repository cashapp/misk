package misk.web.actions

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Request
import misk.web.Response
import misk.web.toMisk
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * UpstreamResourceInterceptor
 *
 * Rules
 * - No overlapping mapping prefixes
 *    "_admin/config/" and "_admin/config/subtab/" will not resolve consistently
 *
 * - All upstreamBaseUrl ends with "/"
 * - All local prefix mappings end with "/"
 *
 *
 */


class UpstreamResourceInterceptor(
  private val client: OkHttpClient,
  private val mappings: List<Mapping>
) : NetworkInterceptor {
//  TODO(adrw) enforce no prefix overlapping https://github.com/square/misk/issues/303
  override fun intercept(chain: NetworkChain): Response<*> {
    val matchedMapping = findMappingMatch(chain) ?: return chain.proceed(chain.request)
    val proxyUrl = HttpUrl.parse(
        matchedMapping.upstreamBaseUrl.toString().dropLast(1)
            + chain.request.url.encodedPath())!!
    return forwardRequestTo(chain.request, proxyUrl)
  }

  private fun findMappingMatch(chain: NetworkChain): Mapping? {
    for (mapping in mappings) {
      if (!chain.request.url.encodedPath().startsWith(mapping.localPathPrefix)) continue
      return mapping
    }
    return null
  }

  fun forwardRequestTo(request: Request, proxyUrl: HttpUrl): Response<*> {
    val clientRequest = request.toOkHttp3().withUrl(proxyUrl)
    return try {
      val clientResponse = client.newCall(clientRequest).execute()
      clientResponse.toMisk()
    } catch (e: IOException) {
      Response(
          "Failed to fetch upstream URL ${clientRequest.url()}".toResponseBody(),
          Headers.of("Content-Type", "text/plain; charset=utf-8"),
          HttpURLConnection.HTTP_UNAVAILABLE
      )
    }
  }

  fun okhttp3.Request.withUrl(newUrl: HttpUrl): okhttp3.Request {
    return newBuilder()
        .url(newUrl)
        .build()
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
          upstreamBaseUrl.encodedPath().endsWith("/") &&
          upstreamBaseUrl.pathSegments().size == 1)
    }
  }

  class Factory @Inject internal constructor() : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      return UpstreamResourceInterceptor(okhttp3.OkHttpClient(), mutableListOf<Mapping>(
          UpstreamResourceInterceptor.Mapping("/_admin/test/", HttpUrl.parse("http://localhost:8000/")!!),
          UpstreamResourceInterceptor.Mapping("/_admin/dashboard/", HttpUrl.parse("http://localhost:3100/")!!),
          UpstreamResourceInterceptor.Mapping("/_admin/config/", HttpUrl.parse("http://localhost:3200/")!!)
      ))
    }
  }
}