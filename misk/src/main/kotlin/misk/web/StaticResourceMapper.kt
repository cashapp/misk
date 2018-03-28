package misk.web

import misk.resources.ResourceLoader
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import okio.Okio
import java.io.File
import java.util.List
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticResourceMapper {
  @Inject lateinit var entries: List<out Entry>

  /** Returns true if the mapped path exists on either the resource path or file system. */
  private fun exists(urlPath: String): Boolean {
    val staticResource = staticResource(urlPath) ?: return false
    val file = File(staticResource.filesystemPath(urlPath))
    return ResourceLoader.exists(staticResource.resourcePath(urlPath))
        || (!file.isDirectory && file.exists())
  }

  /** Returns a source to the mapped path, or null if it doesn't exist. */
  fun open(urlPath: String): BufferedSource? {
    val staticResource = staticResource(urlPath) ?: return null
    val resourcePath = staticResource.resourcePath(urlPath)
    val responseBodyFile = File(staticResource.filesystemPath(urlPath))

    return when {
      ResourceLoader.exists(resourcePath) -> ResourceLoader.open(
          resourcePath)!!
      responseBodyFile.exists() -> Okio.buffer(Okio.source(responseBodyFile))
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
    return when (extension) {
      "html" -> MediaType.parse("text/html")!!
      "css" -> MediaType.parse("text/css")!!
      "js" -> MediaType.parse("application/javascript")!!
      else -> MediaType.parse("application/octet-stream")!!
    }
  }

  private fun staticResource(urlPath: String): Entry? {
    for (staticResource in entries) {
      if (urlPath.startsWith(staticResource.urlPrefix)) return staticResource
    }
    return null
  }

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