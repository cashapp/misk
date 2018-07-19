package misk.resources

import com.google.common.collect.ImmutableSet
import misk.resources.ResourceLoader.Backend
import okio.BufferedSource
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResourceLoader is a testable API for loading resources from the classpath, from the filesystem,
 * from memory, or from another [Backend] source.
 *
 * Resource paths look like UNIX filesystem paths: `/resources/migrations/v1.sql`. Paths always
 * start with a backend name like `/resources` or `/memory` and are followed by a path that is
 * backend-specific. This path may have any number of segments.
 *
 * **Classpath resources** have paths prefixed with `/resources`. The backend reads data from the
 * `src/main/resources` of the project's modules and the contents of all library `.jar` files.
 * Classpath resources are read-only.
 *
 * **Filesystem resources** have paths prefixed with `/filesystem`. The backend reads data from the
 * host machine's local filesystem. It is read-only and does not support [list].
 *
 * **Memory resources** have paths prefixed with `/memory`. The backend starts empty and is
 * populated by calls to [put].
 *
 * Other backends are permitted. They should be registered with a `MapBinder` with the backend name
 * like `/resources` as the key.
 */
@Singleton
class ResourceLoader @Inject constructor(
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  private val backends: java.util.Map<String, Backend>
) {
  init {
    for (prefix in backends.keySet()) {
      require(prefix.matches(Regex("/[^/]+")))
    }
  }

  /** Return a buffered source for `path`, or null if no such resource exists. */
  fun open(path: String): BufferedSource? {
    checkPath(path)

    val (prefix, suffix) = splitPath(path)
    val backend = backends[prefix] ?: return null
    return backend.open(suffix)
  }

  /** Writes a resource as UTF-8. Throws if the backend is readonly. */
  fun put(path: String, utf8: String) {
    put(path, ByteString.encodeUtf8(utf8))
  }

  /** Writes a resource. Throws if the backend is readonly. */
  fun put(path: String, data: ByteString) {
    checkPath(path)

    val (prefix, suffix) = splitPath(path)
    val backend = backends[prefix] ?: return
    backend.put(suffix, data)
  }

  /** Returns true if a resource at `path` exists. */
  fun exists(path: String): Boolean {
    checkPath(path)

    val (prefix, suffix) = splitPath(path)
    val backend = backends[prefix] ?: return false
    return backend.exists(suffix)
  }

  /**
   * Returns the full path of the resources that are immediate children of `path`. This path must
   * start and end with `/`.
   */
  fun list(path: String): List<String> {
    checkPath(path)

    val (prefix, suffix) = splitPath(path)
    val backend = backends[prefix] ?: return listOf()
    return backend.list(suffix).map { prefix + it }
  }

  /**
   * Return the contents of `path` as a string, or null if no such resource exists. Note that this
   * method decodes the resource on every use. It is the caller's responsibility to cache the result
   * if it is to be loaded frequently.
   */
  fun utf8(path: String): String? {
    val source = open(path) ?: return null
    return source.use { it.readUtf8() }
  }

  private fun checkPath(path: String) {
    require(path.matches(Regex("(/[^/]+)+/?"))) { "unexpected path $path" }
  }

  /**
   * Splits a path like `/resources/migrations/v1.sql` into a backend prefix like `/resources` and a
   * backend-specific path like `/migrations/v1.sql`.
   */
  private fun splitPath(path: String): SplitPath {
    val slash = path.indexOf('/', 1)
    return SplitPath(path.substring(0, slash), path.substring(slash))
  }

  private data class SplitPath(val prefix: String, val suffix: String)

  abstract class Backend {
    abstract fun open(path: String): BufferedSource?

    abstract fun exists(path: String): Boolean

    open fun put(path: String, data: ByteString) {
      throw UnsupportedOperationException("cannot put $path; ${this::class} is readonly")
    }

    open fun all(): Set<String> {
      throw UnsupportedOperationException("${this::class} doesn't support all()")
    }

    open fun list(path: String): List<String> {
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
}