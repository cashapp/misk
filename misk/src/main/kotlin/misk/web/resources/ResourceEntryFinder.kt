package misk.web.resources

import misk.web.dashboard.ValidWebEntry
import misk.web.proxy.WebProxyEntry
import okhttp3.HttpUrl
import javax.inject.Inject

class ResourceEntryFinder @Inject constructor(
  private val webProxyEntries: List<WebProxyEntry>,
  private val staticResourceEntries: List<StaticResourceEntry>
) {
  /**
   * @return entry whose url_path_prefix most closely matches given url; longest match wins
   */
  fun staticResource(url: HttpUrl): ValidWebEntry? =
      findEntryFromUrlString(staticResourceEntries, url.encodedPath)

  /**
   * @return entry whose url_path_prefix most closely matches given url; longest match wins
   */
  fun webProxy(url: HttpUrl): ValidWebEntry? =
      findEntryFromUrlString(webProxyEntries, url.encodedPath)

  private fun findEntryFromUrlString(entries: List<ValidWebEntry>, urlPath: String): ValidWebEntry? {
    val results = entries
        .filter { urlPath.startsWith(it.url_path_prefix) }
        .sortedByDescending { it.url_path_prefix.length }
    // Error if there are overlapping identical matched prefixes https://github.com/square/misk/issues/303
    require(results.size <= 1 || results[0].url_path_prefix != results[1].url_path_prefix)
    return results.firstOrNull()
  }
}
