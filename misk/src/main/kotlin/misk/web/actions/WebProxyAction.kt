package misk.web.actions

import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.Post
import misk.web.Request
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import misk.web.resources.ResourceInterceptorCommon
import misk.web.toMisk
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.HttpURLConnection
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
class WebProxyAction : WebAction {
  @Inject private lateinit var entries: List<WebProxyEntry>
  @Inject @JvmSuppressWildcards private lateinit var request: ActionScoped<Request>
  @Named("web_proxy_interceptor") private lateinit var client: OkHttpClient

  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun action(): Response<ResponseBody> {
//    find matching entry
    val matchedEntry = ResourceInterceptorCommon.findEntryFromUrl(entries,
        request.get().url) as WebProxyEntry?
        ?: return noEntryMatchResponse(request.get())
    val proxyUrl = matchedEntry.web_proxy_url.newBuilder()
        .encodedPath(request.get().url.encodedPath())
        .query(request.get().url.query())
        .build()
    return forwardRequestTo(request.get(), proxyUrl)
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
  private fun forwardRequestTo(request: Request, proxyUrl: HttpUrl): Response<ResponseBody> {
    val clientRequest = request.toOkHttp3().withUrl(proxyUrl)
    return try {
      val clientResponse = client.newCall(clientRequest).execute()
      clientResponse.toMisk()
    } catch (e: IOException) {
      fetchFailResponse(clientRequest)
    }
  }

  private fun noEntryMatchResponse(clientRequest: Request): Response<ResponseBody> {
    return Response(
        "No matching WebProxyEntry to forward upstream URL ${clientRequest.url}".toResponseBody(),
        Headers.of("Content-Type", "text/plain; charset=utf-8"),
        HttpURLConnection.HTTP_UNAVAILABLE
    )
  }

  private fun fetchFailResponse(clientRequest: okhttp3.Request): Response<ResponseBody> {
    return Response(
        "Failed to fetch upstream URL ${clientRequest.url()}".toResponseBody(),
        Headers.of("Content-Type", "text/plain; charset=utf-8"),
        HttpURLConnection.HTTP_UNAVAILABLE
    )
  }

  private fun okhttp3.Request.withUrl(newUrl: HttpUrl): okhttp3.Request {
    return newBuilder()
        .url(newUrl)
        .build()
  }
}

