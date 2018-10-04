package misk.web

import com.google.common.base.CharMatcher

/**
 * - slug must be valid slug (lowercase and no white space)
 * - url_path_prefix must start and end with "/"
 */
abstract class WebTab(
  val slug: String,
  url_path_prefix: String
) : UrlPathPrefixEntry(url_path_prefix) {
  init {
    // Requirements enforce the guidelines outlined at top of the file
    require(CharMatcher.inRange('a', 'z').matchesAllOf(slug))
  }
}