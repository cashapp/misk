package misk.resources

import com.google.common.collect.ImmutableSet
import okio.BufferedSource

/**
 * Load resources from the classpath and filesystem.
 *
 * This class is a process-wide static singleton because the classpath and filesystem don't change
 * while the process is running, and because scanning the classpath isn't necessarily very fast!
 */
abstract class ResourceLoader {
  /** Returns the paths of all resources. */
  abstract fun all(): Set<String>

  /** Returns true if a resource at `path` exists. */
  fun exists(path: String) = all().contains(path)

  /** Return a buffered source for `path`, or null if no such resource exists. */
  abstract fun open(path: String): BufferedSource?

  /**
   * Return the contents of `path` as a string, or null if no such resource exists. Note that this
   * method decodes the resource on every use. It is the caller's responsibility to cache the
   * result if it is to be loaded frequently.
   */
  abstract fun utf8(path: String): String?

  /** Returns the full path of the resources that are immediate children of `path`. */
  fun list(path: String): List<String> {
    val prefix = if (path.endsWith("/")) path else "$path/"

    val result = ImmutableSet.builder<String>()
    for (key in all()) {
      if (!key.startsWith(prefix)) continue
      val slash = key.indexOf('/', prefix.length)
      if (slash == -1) {
        result.add(key)
      } else {
        result.add(key.substring(0, slash))
      }
    }
    return result.build().toList()
  }
}
