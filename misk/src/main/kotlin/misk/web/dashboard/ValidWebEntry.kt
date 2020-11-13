package misk.web.dashboard

import com.google.common.base.CharMatcher

private val BlockedUrlPathPrefixes = listOf("/api/")

abstract class ValidWebEntry(val slug: String = "", val url_path_prefix: String = "/") {
  init {
    // internal link url_path_prefix must start and end with '/'
    require(url_path_prefix.startsWith("http") || url_path_prefix.matches(Regex("(/[^/]+)*/"))) {
      "Invalid or unexpected url path prefix: '$url_path_prefix'. Must start with 'http' OR start and end with '/'."
    }

    // url_path_prefix must not be in the blocked list of prefixes to prevent forwarding conflicts with webactions
    require(BlockedUrlPathPrefixes.all { !url_path_prefix.startsWith(it) }) {
      "Url path prefix begins with a blocked prefix: ${BlockedUrlPathPrefixes.filter { url_path_prefix.startsWith(it) }}."
    }

    // slug must must only contain characters in ranges [a-z], [0-9] or '-'
    require(CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9')).or(
        CharMatcher.`is`('-')).matchesAllOf(slug)) {
      "Slug contains invalid characters. Can only contain characters in ranges [a-z], [0-9] or '-'."
    }
  }

  companion object {
    /** Generate a valid slug from an Annotation class */
    inline fun <reified A: Annotation>slugify() = A::class.simpleName!!
      .toString()
      .slugify()

    /** Generate a valid slug from a String */
    fun String.slugify() = this
      .toLowerCase()
      .replace(".", "-")
      .replace("_", "-")
  }
}
