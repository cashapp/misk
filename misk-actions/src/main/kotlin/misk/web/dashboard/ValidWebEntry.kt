package misk.web.dashboard

import com.google.common.base.CharMatcher
import kotlin.reflect.KClass

private val BlockedUrlPathPrefixes = listOf("/api/")

open class ValidWebEntry @JvmOverloads constructor(
  val valid_slug: String = "",
  val valid_url_path_prefix: String = "/"
) {
  init {
    valid_url_path_prefix.requireValidUrlPathPrefix()
    valid_slug.requireValidSlug()
  }

  companion object {
    /** Generate a valid slug from an Annotation class. */
    fun slugify(annotation: KClass<out Annotation>) = annotation.simpleName!!
      .slugify()

    /** Generate a valid slug from an Annotation class. */
    inline fun <reified A : Annotation> slugify() = slugify(A::class)

    /** Generate a valid slug from a String. */
    fun String.slugify() = this
      .lowercase()
      .replace(" ", "-")
      .replace(".", "-")
      .replace("_", "-")
      .replace("/", "")
      .replace(":", "")

    /** Slug must must only contain characters in ranges [a-z], [0-9] or '-'. */
    private fun String.requireValidSlug() = apply {
      require(
        CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9')).or(CharMatcher.`is`('-'))
          .matchesAllOf(this)
      ) {
        "[slug=$this] contains invalid characters. Can only contain characters in ranges [a-z], [0-9] or '-'."
      }
    }

    /** Valid URL path prefix must have correct form and not use any blocked prefixes. */
    private fun String.requireValidUrlPathPrefix() = apply {
      require(this.startsWith("http") ||
        // Internal link url_path_prefix must start and end with '/'
        this.matches(Regex("(/[^/]+)*/")) ||
        // Ignores any trailing query parameters for service local links like /_admin/metadata/?q=config but still enforces the trailing '/'
        if (this.contains("?")) this.split("?").dropLast(1).joinToString("?").matches(Regex("(/[^/]+)*/")) else false
      ) {
        "Invalid or unexpected [urlPathPrefix=$this]. " +
          "Must start with 'http' OR start and end with '/'."
      }

      // url_path_prefix must not be in the blocked list of prefixes to prevent forwarding conflicts with webactions
      require(BlockedUrlPathPrefixes.all { !this.startsWith(it) }) {
        "[urlPathPrefix=$this] begins with a blocked prefix: ${
          BlockedUrlPathPrefixes.filter { this.startsWith(it) }
        }."
      }
    }
  }
}
