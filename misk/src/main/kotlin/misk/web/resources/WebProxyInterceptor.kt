package misk.web.resources

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
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * WebProxyInterceptor
 *
 * Guidelines
 * - No overlapping mapping prefixes
 *    "/_admin/config/" and "/_admin/config/subtab/" will not resolve consistently
 * - url_path_prefix starts and ends with "/"
 * - web_proxy_url ends with "/" and doesn't contain any path segments
 *
 * Expected Functionality
 * - Mappings following above rules are used to initialize interceptor
 * - Interceptor attempts to findMappingFromUrl incoming request paths against mappings
 * - If findMappingFromUrl found, incoming request path is appended to host + port of mapping.server_url
 * - Else, request proceeds
 */

@Singleton
class WebProxyInterceptor private constructor(
  private val client: OkHttpClient,
  private val mappings: List<Mapping> = listOf()
) : NetworkInterceptor {

  //  TODO(adrw) enforce no prefix overlapping https://github.com/square/misk/issues/303
  override fun intercept(chain: NetworkChain): Response<*> {
    val matchedMapping =
        ResourceInterceptorCommon.findMappingFromUrl(mappings, chain.request.url) as Mapping?
            ?: return chain.proceed(chain.request)
    val proxyUrl = matchedMapping.web_proxy_url.newBuilder()
        .encodedPath(chain.request.url.encodedPath())
        .query(chain.request.url.query())
        .build()
    return forwardRequestTo(chain.request, proxyUrl)
  }

  fun forwardRequestTo(request: Request, proxyUrl: HttpUrl): Response<*> {
    val clientRequest = request.toOkHttp3().withUrl(proxyUrl)
    return try {
      val clientResponse = client.newCall(clientRequest).execute()
      clientResponse.toMisk()
    } catch (e: IOException) {
      fetchFailResponse(clientRequest)
    }
  }

  private fun fetchFailResponse(clientRequest: okhttp3.Request): Response<ResponseBody> {
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
   * web_proxy_url: `http://localhost:3000/`
   *
   * An incoming request then for `/_admin/config.js` would route to `http://localhost:3000/_admin/config.js`.
   *
   *
   * This data class is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<WebProxyInterceptor.Mapping>().toInstance(WebProxyInterceptor.Mapping(...))
   * ```
   */
  data class Mapping(
    override val url_path_prefix: String,
    val web_proxy_url: HttpUrl
  ) : ResourceInterceptorCommon.Mapping {
    init {
      require(url_path_prefix.startsWith("/") &&
          url_path_prefix.endsWith("/") &&
          !url_path_prefix.startsWith("/api/") &&
          web_proxy_url.encodedPath().endsWith("/") &&
          web_proxy_url.pathSegments().size == 1)
    }
  }

  class Factory @Inject internal constructor(
    @Named("web_proxy_interceptor") val httpClient: OkHttpClient,
    var mappings: List<Mapping>
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      // TODO(adrw) jk above. transition webproxyinterceptor -> webproxyactions / resourceinterceptor+staticresourceinterceptor -> resourceactions
      return WebProxyInterceptor(httpClient, mappings)
    }
  }
}

