package misk.web.actions

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Request
import misk.web.Response
import misk.web.ResponseBody
import misk.web.toMisk
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UpstreamResourceInterceptor
 *
 * Rules
 * - No overlapping mapping prefixes
 *    "/_admin/config/" and "/_admin/config/subtab/" will not resolve consistently
 * - All server_url ends with "/"
 * - All local prefix mappings end with "/"
 *
 * Expected Functionality
 * - Mappings following above rules are used to initialize interceptor
 * - Interceptor attempts to match incoming request paths against mappings
 * - If match found, incoming request path is appended to host + port of mapping.server_url
 * - Else, request proceeds
 */

@Singleton
class UpstreamResourceInterceptor(
  private val client: OkHttpClient,
  private val mappings: List<Mapping> = listOf()
) : NetworkInterceptor {

  //  TODO(adrw) enforce no prefix overlapping https://github.com/square/misk/issues/303
  override fun intercept(chain: NetworkChain): Response<*> {
    val matchedMapping = findMappingMatch(chain) ?: return chain.proceed(chain.request)
    val proxyUrl = matchedMapping.upstreamBaseUrl.newBuilder()
        .encodedPath(chain.request.url.encodedPath())
        .query(chain.request.url.query())
        .build()
    return when (matchedMapping.upstreamMode) {
      Mode.SERVER -> forwardRequestTo(chain.request, proxyUrl)
      // TODO(adrw) build out JAR resource getting and returning in a response
      Mode.JAR -> FetchFailResponse(chain.request.toOkHttp3())
    }
  }

  private fun findMappingMatch(chain: NetworkChain): Mapping? {
    // TODO(adrw) check if there is a predictability if there are overlapping mappings ie. last mapping is fall through
    // main case: /_admin/ should not pick up all sub paths but should still return index
    // else build out (*) REGEX mapping
    for (mapping in mappings) {
      if (chain.request.url.encodedPath().startsWith(mapping.urlPathPrefix)) return mapping
    }
    return null
  }

  fun forwardRequestTo(request: Request, proxyUrl: HttpUrl): Response<*> {
    val clientRequest = request.toOkHttp3().withUrl(proxyUrl)
    return try {
      val clientResponse = client.newCall(clientRequest).execute()
      clientResponse.toMisk()
    } catch (e: IOException) {
      FetchFailResponse(clientRequest)
    }
  }

  private fun FetchFailResponse(clientRequest: okhttp3.Request): Response<ResponseBody> {
    return Response(
        "Failed to fetch upstream URL ${clientRequest.url()}".toResponseBody(),
        Headers.of("Content-Type", "text/plain; charset=utf-8"),
        HttpURLConnection.HTTP_UNAVAILABLE
    )
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
   * url_path_prefix: `/_admin/`
   * server_url: `http://localhost:3000/`
   *
   * An incoming request then for `/_admin/config.js` would route to `http://localhost:3000/_admin/config.js`.
   */
  // Make register tab a subclass of mapping
  data class Mapping(
    val urlPathPrefix: String,
    val upstreamBaseUrl: HttpUrl,
    val upstreamBaseJar: String,
    val upstreamMode: Mode
  ) {
    init {
      require(urlPathPrefix.startsWith("/") &&
          urlPathPrefix.endsWith("/") &&
          !urlPathPrefix.startsWith("/api/") &&
          upstreamBaseUrl.encodedPath().endsWith("/") &&
          upstreamBaseUrl.pathSegments().size == 1 &&
          upstreamBaseJar.startsWith("/") &&
          upstreamBaseJar.endsWith("/"))
    }
  }

  enum class Mode {
    SERVER,
    JAR
  }

  class Factory @Inject internal constructor() : NetworkInterceptor.Factory {
    @Inject private lateinit var mappings: List<Mapping>
    override fun create(action: Action): NetworkInterceptor? {
      return UpstreamResourceInterceptor(okhttp3.OkHttpClient(), mappings)
    }
  }
}

