package misk.web.resources

import misk.resources.ResourceLoader
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebEntryCommon
import misk.web.actions.WebEntryCommon.findEntryFromUrl
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

@Singleton
class StaticResourceMapper @Inject internal constructor(
  private val resourceLoader: ResourceLoader,
  private val entries: MutableList<out Entry>
) {
  /** Returns true if the mapped path exists on either the resource path or file system. */
  private fun exists(urlPath: String): Kind {
    val staticResource = staticResource(urlPath) ?: return Kind.NO_MATCH
    val resourcePath = staticResource.resourcePath(urlPath)
    if (resourceLoader.exists(resourcePath)) return Kind.RESOURCE
    if (resourceLoader.list(resourcePath).isNotEmpty()) return Kind.RESOURCE_DIRECTORY
    return Kind.NO_MATCH
  }

  enum class Kind {
    NO_MATCH, RESOURCE, RESOURCE_DIRECTORY,
  }

  /** Returns a source to the mapped path, or null if it doesn't exist. */
  fun open(urlPath: String): BufferedSource? {
    val staticResource = staticResource(urlPath) ?: return null
    val resourcePath = staticResource.resourcePath(urlPath)
    return when {
      resourceLoader.exists(resourcePath) -> resourceLoader.open(resourcePath)!!
      else -> null
    }
  }

  fun getResponse(urlPath: String): Response<ResponseBody>? {
    return getResponse(HttpUrl.get(urlPath))
  }

  fun getResponse(url: HttpUrl): Response<ResponseBody>? {
    val urlPath = url.encodedPath()
    val matchedEntry = findEntryFromUrl(entries, url) as Entry?
    return when (exists(normalizePath(urlPath))) {
      StaticResourceMapper.Kind.NO_MATCH -> when {
        !urlPath.startsWith("/api") && !urlPath.contains(".") && !urlPath.endsWith("/") -> redirectResponse(normalizePathWithQuery(url))
        // actually return the resource, don't redirect. Path must stay the same since this will be handled by React router
        !urlPath.startsWith("/api") && !urlPath.contains(".") && urlPath.endsWith("/") -> {

          resourceResponse(normalizePath(matchedEntry?.url_path_prefix ?: urlPath))
        }
        else -> null
      }
      StaticResourceMapper.Kind.RESOURCE -> resourceResponse(normalizePath(urlPath))
      StaticResourceMapper.Kind.RESOURCE_DIRECTORY -> redirectResponse(normalizePathWithQuery(url))
    }
  }

  private fun normalizePath(urlPath: String): String {
    return when {
      //    /_admin/ -> /_admin/index.html
      urlPath.endsWith("/") -> "${urlPath}index.html"
      //    /_admin/config -> /_admin/config/
      !urlPath.startsWith("/api") && !urlPath.contains(".") && !urlPath.endsWith("/") -> "$urlPath/"
      // /_admin/index.html -> /_admin/index.html
      else -> urlPath
    }
  }

  private fun normalizePathWithQuery(url: HttpUrl): String {
    return if (url.encodedQuery().isNullOrEmpty()) normalizePath(url.encodedPath()) else normalizePath(url.encodedPath()) + "?" + url.encodedQuery()
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

  private fun staticResource(urlPath: String): Entry? {
    return WebEntryCommon.findEntryFromUrl(entries, urlPath) as Entry?
  }

  /**
   * This data class is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<StaticResourceMapper.Entry>().toInstance(StaticResourceMapper.Entry(...))
   * ```
   */
  data class Entry(
    override val url_path_prefix: String,
    private val resourcePath: String
  ) : WebEntryCommon.Entry {
    init {
      require(url_path_prefix.endsWith("/") &&
      url_path_prefix.startsWith("/") &&
          // don't allow anyone to multibind entry prefix to "/" since NotFound and other actions will never be reached
      url_path_prefix.length > 1)
    }

    fun resourcePath(urlPath: String): String {
      return if (resourcePath.endsWith("/")) resourcePath + urlPath.removePrefix(url_path_prefix) else resourcePath + "/" + urlPath.removePrefix(url_path_prefix)
    }
  }
}