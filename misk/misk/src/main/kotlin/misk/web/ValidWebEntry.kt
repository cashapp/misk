package misk.web

import com.google.common.base.CharMatcher

private val BlockedUrlPathPrefixes = listOf("/api/")

abstract class ValidWebEntry(val slug: String = "", val url_path_prefix: String = "") {
  init {
    // internal link url_path_prefix must start and end with '/'
    require(url_path_prefix.startsWith("http") || url_path_prefix.matches(Regex("(/[^/]+)*/"))) {
      "invalid or unexpected url path prefix: $url_path_prefix"
    }

    // url_path_prefix must not be in the blocked list of prefixes to prevent forwarding conflicts with webactions
    require(BlockedUrlPathPrefixes.all { !url_path_prefix.startsWith(it) }) {
      "url path prefix begins with a blocked prefix: $BlockedUrlPathPrefixes"
    }

    // slug must must only contain characters in ranges [a-z], [0-9] or '-'
    require(CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9')).or(
        CharMatcher.`is`('-')).matchesAllOf(slug)) {
      "slug contains invalid characters. Can only contain characters in ranges [a-z], [0-9] or '-'"
    }
  }
}
