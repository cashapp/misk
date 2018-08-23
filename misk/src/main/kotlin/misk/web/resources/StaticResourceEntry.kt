package misk.web.resources

/**
 * This data class is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<StaticResourceEntry>().toInstance(StaticResourceEntry(...))
 * ```
 */
data class StaticResourceEntry(
  override val url_path_prefix: String,
  private val resourcePath: String
) : ResourceEntryCommon.Entry {
  init {
    ResourceEntryCommon.requireValidUrlPathPrefix(url_path_prefix)
  }

  fun resourcePath(urlPath: String): String {
    return resourcePath + urlPath.removePrefix(url_path_prefix)
//    return if (resourcePath.endsWith("/")) resourcePath + urlPath.removePrefix(url_path_prefix) else resourcePath + "/" + urlPath.removePrefix(url_path_prefix)
  }
}