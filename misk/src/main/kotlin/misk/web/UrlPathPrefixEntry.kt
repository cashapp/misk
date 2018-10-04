package misk.web

private val BlockedUrlPathPrefixes = listOf("/api/")

abstract class UrlPathPrefixEntry(val url_path_prefix: String) {
  init {
    // url_path_prefix must start and end with '/'
    require(url_path_prefix.matches(Regex("(/[^/]+)*/"))) {
      "invalid or unexpected url path prefix: $url_path_prefix"
    }
    // url_path_prefix must not be in the blocked list of prefixes to prevent forwarding conflicts with webactions
    require(BlockedUrlPathPrefixes.all { !url_path_prefix.startsWith(it) }) {
      "url path prefix begins with a blocked prefix: $BlockedUrlPathPrefixes"
    }
  }
}