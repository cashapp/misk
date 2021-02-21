package misk.web.proxy

import com.google.inject.Inject
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.HttpCall
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.NotFoundAction
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import misk.web.resources.ResourceEntryFinder
import misk.web.resources.StaticResourceAction
import misk.web.toMisk
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Named
import javax.inject.Singleton

/**
 * WebProxyAction
 *
 * Guidelines
 * - Overlapping entry prefixes will resolve to the longest match
 *     Example
 *     Entries: `/_admin/config/`, `/_admin/config/subtab/`
 *     Request: `/_admin/config/subtab/app.js` will resolve to the `/_admin/config/subtab/` entry
 * - url_path_prefix starts with "/"
 * - url_path_prefix ends with "/"
 * - web_proxy_url ends with "/" and doesn't contain any path segments
 *
 * Expected Functionality
 * - Entries following above rules are injected into action
 * - Action attempts to findEntryFromUrl incoming request paths against entries
 * - If findEntryFromUrl found, incoming request path is appended to host + port of StaticResourceEntry.server_url
 * - Else, 404
 */

@Singleton
class WebProxyAction @Inject constructor(
  private val optionalBinder: OptionalBinder,
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
  private val staticResourceAction: StaticResourceAction,
  private val resourceEntryFinder: ResourceEntryFinder
) : WebAction {

  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun action(): Response<ResponseBody> {
    val httpCall = clientHttpCall.get()
    val matchedEntry = resourceEntryFinder.webProxy(httpCall.url) as WebProxyEntry?
      ?: return NotFoundAction.response(httpCall.url.toString())
    val proxyUrl = matchedEntry.web_proxy_url.newBuilder()
      .encodedPath(httpCall.url.encodedPath)
      .query(httpCall.url.query)
      .build()
    return forwardRequestTo(proxyUrl)
  }

  private fun forwardRequestTo(proxyUrl: HttpUrl): Response<ResponseBody> {
    val httpCall = clientHttpCall.get()
    val proxyRequest = httpCall.asOkHttpRequest().forwardedWithUrl(proxyUrl)
    return try {
      optionalBinder.proxyClient.newCall(proxyRequest).execute().toMisk()
    } catch (e: IOException) {
      staticResourceAction.getResponse(httpCall)
    }
  }

  private fun fetchFailResponse(clientRequestUrl: HttpUrl): Response<ResponseBody> {
    return Response(
      "Failed to fetch upstream URL $clientRequestUrl".toResponseBody(),
      headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8.asMediaType().toString()),
      HttpURLConnection.HTTP_UNAVAILABLE
    )
  }

  private fun okhttp3.Request.forwardedWithUrl(newUrl: HttpUrl): okhttp3.Request {
    // TODO(adrw) include the client URL/IP as the for= field for Forwarded
    return newBuilder()
      .addHeader(
        "Forwarded",
        "for=; by=${
        HttpUrl.Builder()
          .scheme(this.url.scheme)
          .host(this.url.host)
          .port(this.url.port)
        }"
      )
      .url(newUrl)
      .build()
  }
}

/**
 * https://github.com/google/guice/wiki/FrequentlyAskedQuestions#how-can-i-inject-optional-parameters-into-a-constructor
 */
@Singleton
class OptionalBinder @Inject constructor() {
  @Inject(optional = true)
  @field:Named("web_proxy_action")
  var proxyClient: OkHttpClient = OkHttpClient()
}
