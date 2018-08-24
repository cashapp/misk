package misk.web.resources

import okhttp3.HttpUrl

object ResourceEntryCommon {
  private fun findEntryFromUrlString(entries: List<Entry>, urlPath: String): Entry? {
    val results = entries
        .filter { urlPath.startsWith(it.url_path_prefix) }
        .sortedByDescending { it.url_path_prefix.length }
    // Error if there are overlapping identical matched prefixes https://github.com/square/misk/issues/303
    if (results.size > 1) require(results[0].url_path_prefix != results[1].url_path_prefix)
    return results.firstOrNull()
  }

  /**
   * findEntryFromUrl
   *
   * @return entry whose url_path_prefix most closely matches given url; longest match wins
   */
  fun findEntryFromUrl(entries: List<Entry>, url: HttpUrl): Entry? {
    return (findEntryFromUrlString(entries, url.encodedPath()))
  }

  fun findEntryFromUrl(entries: List<Entry>, url: String): Entry? {
    return (findEntryFromUrlString(entries, url))
  }

  private val BlockedUrlPathPrefixes = listOf("/api/")
  fun requireValidUrlPathPrefix(url_path_prefix: String) {
    require(url_path_prefix.matches(Regex("(/[^/]+)*/"))) {
      "invalid or unexpected url path prefix: $url_path_prefix"
    }
    require(BlockedUrlPathPrefixes.all { !url_path_prefix.startsWith(it) }) {
      "url path prefix begins with a blocked prefix: ${BlockedUrlPathPrefixes}"
    }
  }

  interface Entry {
    val url_path_prefix: String
  }
}