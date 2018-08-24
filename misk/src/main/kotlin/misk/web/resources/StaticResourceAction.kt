package misk.web.resources

import misk.resources.ResourceLoader
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.Post
import misk.web.Request
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.NotFoundAction
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StaticResourceAction
 *
 * This data class is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<StaticResourceEntry>().toInstance(StaticResourceEntry(...))
 * ```
 */

@Singleton
class StaticResourceAction : WebAction {
  @Inject private lateinit var entries: List<StaticResourceEntry>
  @Inject @JvmSuppressWildcards private lateinit var clientRequest: ActionScoped<Request>
  @Inject private lateinit var resourceLoader: ResourceLoader

  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated  // TODO(adrw) should this be unauthenticated?
  fun action(): Response<ResponseBody> {
    val request = clientRequest.get()
    return getResponse(request)
  }

  private fun getResponse(request: Request): Response<ResponseBody> {
    val urlPath = request.url.encodedPath()
    val matchedEntry =
        ResourceEntryCommon.findEntryFromUrl(entries, request.url) as StaticResourceEntry?
    return when (exists(normalizePath(urlPath))) {
      Kind.NO_MATCH -> when {
        !urlPath.contains(".") && !urlPath.endsWith("/") ->
          redirectResponse(normalizePathWithQuery(request.url))
      // actually return the resource, don't redirect. Path must stay the same since this will be handled by React router
        !urlPath.contains(".") && urlPath.endsWith("/") ->
          resourceResponse(normalizePath(matchedEntry?.url_path_prefix ?: urlPath))
        else -> null
      }
      Kind.RESOURCE -> resourceResponse(normalizePath(urlPath))
      Kind.RESOURCE_DIRECTORY -> redirectResponse(normalizePathWithQuery(request.url))
    } ?: NotFoundAction.response(request.url.toString())
  }

  /** Returns true if the mapped path exists on either the resource path or file system. */
  private fun exists(urlPath: String): Kind {
    val staticResource =
        ResourceEntryCommon.findEntryFromUrl(entries, urlPath) as StaticResourceEntry?
            ?: return Kind.NO_MATCH
    val resourcePath = staticResource.resourcePath(urlPath)
    if (resourceLoader.exists(resourcePath)) return Kind.RESOURCE
    if (resourceLoader.list(resourcePath).isNotEmpty()) return Kind.RESOURCE_DIRECTORY
    return Kind.NO_MATCH
  }

  enum class Kind {
    NO_MATCH,
    RESOURCE,
    RESOURCE_DIRECTORY,
  }

  /** Returns a source to the mapped path, or null if it doesn't exist. */
  fun open(urlPath: String): BufferedSource? {
    val staticResource =
        ResourceEntryCommon.findEntryFromUrl(entries, urlPath) as StaticResourceEntry?
            ?: return null
    val resourcePath = staticResource.resourcePath(urlPath)
    return when {
      resourceLoader.exists(resourcePath) -> resourceLoader.open(resourcePath)!!
      else -> null
    }
  }

  private fun normalizePath(urlPath: String): String {
    return when {
      urlPath.endsWith("/") -> "${urlPath}index.html"
      !urlPath.contains(".") && !urlPath.endsWith("/") -> "$urlPath/"
      else -> urlPath
    }
  }

  private fun normalizePathWithQuery(url: HttpUrl): String {
    return if (url.encodedQuery().isNullOrEmpty()) normalizePath(url.encodedPath())
    else normalizePath(url.encodedPath()) + "?" + url.encodedQuery()
  }

  private fun resourceResponse(resourcePath: String): Response<ResponseBody>? {
    return if (exists(resourcePath) == Kind.RESOURCE) {
      val responseBody = object : ResponseBody {
        override fun writeTo(sink: BufferedSink) {
          open(resourcePath)!!.use {
            sink.writeAll(it)
          }
        }
      }
      Response(
          body = responseBody,
          headers = Headers.of("Content-Type", mimeType(resourcePath).toString()))
    } else {
      null
    }
  }

  private fun redirectResponse(urlPath: String): Response<ResponseBody> {
    return Response(
        body = "".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.of("Location", "$urlPath"))
  }

  private fun mimeType(path: String): MediaType {
    val extension = path.substring(path.lastIndexOf('.') + 1)
    return MediaTypes.fromFileExtension(extension) ?: MediaTypes.APPLICATION_OCTETSTREAM_MEDIA_TYPE
  }
}

