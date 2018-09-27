package misk.web.resources

private val BlockedUrlPathPrefixes = listOf("/api/")

abstract class ResourceEntry(val url_path_prefix: String) {
  init {
    require(url_path_prefix.matches(Regex("(/[^/]+)*/"))) {
      "invalid or unexpected url path prefix: $url_path_prefix"
    }
    require(BlockedUrlPathPrefixes.all { !url_path_prefix.startsWith(it) }) {
      "url path prefix begins with a blocked prefix: $BlockedUrlPathPrefixes"
    }
  }
}