package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * WebProxyAction
 *
 * Guidelines
 * - No overlapping entry prefixes
 *    "/_admin/config/" and "/_admin/config/subtab/" will not resolve consistently
 * - url_path_prefix starts and ends with "/"
 * - web_proxy_url ends with "/" and doesn't contain any path segments
 *
 * Expected Functionality
 * - Entrys following above rules are used to initialize interceptor
 * - Interceptor attempts to findEntryFromUrl incoming request paths against entries
 * - If findEntryFromUrl found, incoming request path is appended to host + port of Entry.server_url
 * - Else, request proceeds
 */

@Singleton
class WebProxyAction() : WebAction {
  @Inject lateinit var entries: List<WebProxyEntry>
  @Named("web_proxy_interceptor") private lateinit var client: OkHttpClient

  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun action(@PathParam path: String): Response<ResponseBody> {
    return Response(
        body = "Forwarded request terminated at WebProxyAction for /$path".toResponseBody(),
        headers = Headers.of("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
        statusCode = 404
    )
  }

  //  TODO(adrw) enforce no prefix overlapping https://github.com/square/misk/issues/303
//  fun intercept(chain: NetworkChain): Response<*> {
//    val matchedEntry =
//        ResourceInterceptorCommon.findEntryFromUrl(entries,
//            chain.request.url) as Entry?
//            ?: return chain.proceed(chain.request)
//    val proxyUrl = matchedEntry.web_proxy_url.newBuilder()
//        .encodedPath(chain.request.url.encodedPath())
//        .query(chain.request.url.query())
//        .build()
//    return forwardRequestTo(chain.request, proxyUrl)
//  }
//
//  fun forwardRequestTo(request: Request, proxyUrl: HttpUrl): Response<*> {
//    val clientRequest = request.toOkHttp3().withUrl(proxyUrl)
//    return try {
//      val clientResponse = client.newCall(clientRequest).execute()
//      clientResponse.toMisk()
//    } catch (e: IOException) {
//      fetchFailResponse(clientRequest)
//    }
//  }
//
//  private fun fetchFailResponse(clientRequest: okhttp3.Request): Response<ResponseBody> {
//    return Response(
//        "Failed to fetch upstream URL ${clientRequest.url()}".toResponseBody(),
//        Headers.of("Content-Type", "text/plain; charset=utf-8"),
//        HttpURLConnection.HTTP_UNAVAILABLE
//    )
//  }
//
//  fun okhttp3.Request.withUrl(newUrl: HttpUrl): okhttp3.Request {
//    return newBuilder()
//        .url(newUrl)
//        .build()
//  }

//  class Factory @Inject internal constructor(
//    @Named("web_proxy_interceptor") val httpClient: OkHttpClient,
//    var entries: List<Entry>
//  ) : NetworkInterceptor.Factory {
//    override fun create(action: Action): NetworkInterceptor? {
//      // TODO(adrw) jk above. transition webproxyinterceptor -> webproxyactions / resourceinterceptor+staticresourceinterceptor -> resourceactions
//      return WebProxyAction(entries)
//    }
//  }
}

