package misk.web.resources

import misk.resources.ResourceLoader
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticResourceMapper @Inject internal constructor(
  private val resourceLoader: ResourceLoader,
  private val entries: MutableList<out Entry>
) {
  /** Returns true if the mapped path exists on either the resource path or file system. */
  private fun exists(urlPath: String): Boolean {
    val staticResource = staticResource(urlPath) ?: return false
    val file = File(staticResource.filesystemPath(urlPath))
    return resourceLoader.exists(staticResource.resourcePath(urlPath))
        || (!file.isDirectory && file.exists())
  }

  /** Returns a source to the mapped path, or null if it doesn't exist. */
  fun open(urlPath: String): BufferedSource? {
    val staticResource = staticResource(urlPath) ?: return null
    val resourcePath = staticResource.resourcePath(urlPath)
    val responseBodyFile = File(staticResource.filesystemPath(urlPath))

    return when {
      resourceLoader.exists(resourcePath) -> resourceLoader.open(resourcePath)!!
      responseBodyFile.exists() -> responseBodyFile.source().buffer()
      else -> null
    }
  }

  fun getResponse(urlPath: String): Response<ResponseBody>? {
    if (!exists(urlPath)) {
      return null
    }

    val responseBody = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        open(urlPath)!!.use {
          sink.writeAll(it)
        }
      }
    }

    return Response(
        body = responseBody,
        headers = Headers.of("Content-Type", mimeType(urlPath).toString()))
  }

  private fun mimeType(path: String): MediaType {
    val extension = path.substring(path.lastIndexOf('.') + 1)
    return MediaTypes.fromFileExtension(extension) ?: MediaTypes.APPLICATION_OCTETSTREAM_MEDIA_TYPE
  }

  private fun staticResource(urlPath: String): Entry? {
    return entries.firstOrNull { urlPath.startsWith(it.urlPrefix) }
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
    val urlPrefix: String,
    private val resourcePath: String,
    private val filesystemPath: String
  ) {
    init {
      require(urlPrefix.endsWith("/"))
      require(urlPrefix.startsWith("/"))
    }

    fun resourcePath(urlPath: String): String {
      return resourcePath + urlPath.substring(urlPrefix.length - 1)
    }

    fun filesystemPath(urlPath: String): String {
      // TODO: .. in path
      return filesystemPath + urlPath.substring(urlPrefix.length - 1)
    }
  }
}