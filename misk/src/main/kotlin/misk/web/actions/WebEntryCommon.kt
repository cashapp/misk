package misk.web.actions

import okhttp3.HttpUrl

object WebEntryCommon {
  private fun findEntryFromUrlString(entries: List<Entry>, urlPath: String): Entry? {
    return entries.filter { urlPath.startsWith(it.url_path_prefix) }.sortedByDescending { it.url_path_prefix.length }.firstOrNull()
//    var matched : List<Entry> = listOf()
//    for (entry in entries) {
//      if (urlPath.startsWith(entry.url_path_prefix)) matched += entry
//    }
//    return when {
//      matched.size == 1 -> matched.first()
//      matched.size > 1 -> matched.sortedByDescending { it.url_path_prefix.length }.first()
//      else -> null
//    }
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

  interface Entry {
    val url_path_prefix: String
  }
}