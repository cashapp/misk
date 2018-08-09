package misk.web.actions

import okhttp3.HttpUrl

object WebEntryCommon {
  /**
   * findEntryFromUrl
   *
   * @return entry whose url_path_prefix most closely matches given url; longest match wins
   */
  fun findEntryFromUrl(entries: List<Entry>, url: HttpUrl): Entry? {
    var matched : List<Entry> = listOf()
    for (entry in entries) {
      if (url.encodedPath().startsWith(entry.url_path_prefix)) matched += entry
    }
    return when {
      matched.size == 1 -> matched.first()
      matched.size > 1 -> matched.sortedByDescending { it.url_path_prefix.length }.first()
      else -> null
    }
  }

  interface Entry {
    val url_path_prefix: String
  }
}