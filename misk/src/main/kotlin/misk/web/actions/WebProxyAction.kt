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
import misk.web.mediatype.asMediaType
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
 * - Overlapping entry prefixes will resolve to the longest match
 *     Example
 *     Entries: `/_admin/config`, `/_admin/config/subtab`
 *     Request: `/_admin/config/subtab/app.js` will resolve to the `/_admin/config/subtab` entry
 * - url_path_prefix starts with "/"
 * - url_path_prefix does not end with "/"
 * - web_proxy_url ends with "/" and doesn't contain any path segments
 *
 * Expected Functionality
 * - Entries following above rules are injected into action
 * - Action attempts to findEntryFromUrl incoming clientRequest paths against entries
 * - If findEntryFromUrl found, incoming clientRequest path is appended to host + port of Entry.server_url
 * - Else, 404
 */

@Singleton
class WebProxyAction : WebAction {
  @Inject @Named("web_proxy_action") private lateinit var proxyClient: OkHttpClient
  @Inject private lateinit var entries: List<WebProxyEntry>
  @Inject @JvmSuppressWildcards private lateinit var clientRequest: ActionScoped<Request>

  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()

  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun action(): Response<ResponseBody> {
    //  TODO(adrw) enforce no prefix overlapping https://github.com/square/misk/issues/303
    val request = clientRequest.get()
    val matchedEntry = WebEntryCommon.findEntryFromUrl(entries,
        request.url) as WebProxyEntry?
        ?: return noEntryMatchResponse(request.url)
    val proxyUrl = matchedEntry.web_proxy_url.newBuilder()
        .encodedPath(request.url.encodedPath())
        .query(request.url.query())
        .build()
    return forwardRequestTo(proxyUrl)
  }

  private fun forwardRequestTo(proxyUrl: HttpUrl): Response<ResponseBody> {
    val request = clientRequest.get()
    val proxyRequest = request.toOkHttp3().forwardedWithUrl(proxyUrl)
    return try {
      val proxyResponse = proxyClient.newCall(proxyRequest).execute()
      proxyResponse.toMisk()
    } catch (e: IOException) {
      fetchFailResponse(proxyRequest.url())
    }
  }

  private fun noEntryMatchResponse(clientRequestUrl: HttpUrl): Response<ResponseBody> {
    return Response(
        "WebProxyAction: No matching WebProxyEntry to forward upstream URL $clientRequestUrl".toResponseBody(),
        Headers.of("Content-Type", plainTextMediaType.toString()),
        HttpURLConnection.HTTP_NOT_FOUND
    )
  }

  private fun fetchFailResponse(clientRequestUrl: HttpUrl): Response<ResponseBody> {
    return Response(
        "WebProxyAction: Failed to fetch upstream URL $clientRequestUrl".toResponseBody(),
        Headers.of("Content-Type", plainTextMediaType.toString()),
        HttpURLConnection.HTTP_UNAVAILABLE
    )
  }

  private fun okhttp3.Request.forwardedWithUrl(newUrl: HttpUrl): okhttp3.Request {
    // TODO(adrw) include the client URL/IP as the for= field for Forwarded
    return newBuilder()
        .addHeader("Forwarded", "for=; by=${HttpUrl.Builder()
            .scheme(this.url().scheme())
            .host(this.url().host())
            .port(this.url().port())}")
        .url(newUrl)
        .build()
  }
}

