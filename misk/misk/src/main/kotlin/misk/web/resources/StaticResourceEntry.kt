package misk.web.resources

import misk.web.UrlPathPrefixEntry

/**
 * This data class is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<StaticResourceEntry>().toInstance(StaticResourceEntry(...))
 * ```
 */
class StaticResourceEntry(
  url_path_prefix: String = "/",
  private val resourcePath: String
) : UrlPathPrefixEntry(url_path_prefix) {

  fun resourcePath(urlPath: String): String {
    val normalizedResourcePath = if (!resourcePath.endsWith("/")) "$resourcePath/" else resourcePath
    return normalizedResourcePath + urlPath.removePrefix(url_path_prefix)
  }
}