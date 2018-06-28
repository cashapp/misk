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

class UpstreamResourceInterceptor(
  private val client: OkHttpClient,
  private val mappings: List<Mapping>
) : NetworkInterceptor {
//  TODO(adrw) enforce no prefix overlapping
  override fun intercept(chain: NetworkChain): Response<*> {
    val matchedMapping = findMappingMatch(chain) ?: return chain.proceed(chain.request)
    var proxyEndPath =
        chain.request.url.encodedPath().drop(1).removePrefix(matchedMapping.localPathPrefix)
    if (!chain.request.url.encodedPath().endsWith('/') && !chain.request.url.pathSegments().last().contains('.')) {
        proxyEndPath += '/'
    }
    val proxyUrl = HttpUrl.parse(
        matchedMapping.upstreamBaseUrl.toString() + proxyEndPath)!!
    return forwardRequestTo(chain.request, proxyUrl)
  }

  private fun findMappingMatch(chain: NetworkChain): Mapping? {
    for (mapping in mappings) {
      if (!chain.request.url.encodedPath().startsWith(mapping.localPathPrefix.dropLast(1))) continue
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
          upstreamBaseUrl.encodedPath().endsWith("/"))
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