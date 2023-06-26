package misk.web.resources

import misk.web.dashboard.ValidWebEntry

/**
 * This data class is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<StaticResourceEntry>().toInstance(StaticResourceEntry(...))
 * ```
 */
data class StaticResourceEntry(
  val url_path_prefix: String = "/",
  private val resourcePath: String
) : ValidWebEntry(valid_url_path_prefix = url_path_prefix) {

  fun resourcePath(urlPath: String): String {
    val normalizedResourcePath = if (!resourcePath.endsWith("/")) "$resourcePath/" else resourcePath
    return normalizedResourcePath + urlPath.removePrefix(url_path_prefix)
  }
}
