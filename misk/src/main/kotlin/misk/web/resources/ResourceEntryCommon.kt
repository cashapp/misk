package misk.web.resources

import okhttp3.HttpUrl

object ResourceEntryCommon {
  private val BlockedURLPrefixes = listOf("/api")
  private fun findEntryFromUrlString(entries: List<Entry>, urlPath: String): Entry? {
    val results = entries
        .filter { urlPath.startsWith(it.url_path_prefix) }
        .sortedByDescending { it.url_path_prefix.length }
    // Error if there are overlapping matched prefixes https://github.com/square/misk/issues/303
    if (results.size > 1) require(results[0].url_path_prefix != results[1].url_path_prefix)
    return results.firstOrNull()
  }

  /**
   * findEntryFromUrl
   *
   * @return entry whose url_path_prefix most closely matches given url; longest match wins
   */
  fun findEntryFromUrl(entries: List<Entry>, url: HttpUrl): Entry? {
    return (findEntryFromUrlString(entries,
        url.encodedPath()))
  }

  fun findEntryFromUrl(entries: List<Entry>, url: String): Entry? {
    return (findEntryFromUrlString(entries, url))
  }

  fun requireValidUrlPathPrefix(url_path_prefix: String) {
    require(url_path_prefix.matches(Regex("(/[^/]+)*")) &&
        BlockedURLPrefixes.all { !url_path_prefix.startsWith(it) })
  }

  interface Entry {
    val url_path_prefix: String
  }
}