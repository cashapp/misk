package misk.resources

import com.google.common.reflect.ClassPath
import com.google.common.reflect.ClassPath.ResourceInfo
import okio.BufferedSource
import okio.Okio

/**
 * Load resources from the classpath and filesystem.
 *
 * This class is a process-wide static singleton because the classpath and filesystem don't change
 * while the process is running, and because scanning the classpath isn't necessarily very fast!
 */
object ResourceLoader {
  val resourcesByPath: Map<String, ResourceInfo>

  init {
    val classLoader = ResourceLoader::class.java.classLoader
    val classPath = ClassPath.from(classLoader)
    resourcesByPath = classPath.resources.associateBy { r -> r.resourceName }
  }

  /** Return a buffered source for `path`, or null if no such resource exists. */
  fun open(path: String): BufferedSource? {
    val resource = resourcesByPath[path] ?: return null
    return Okio.buffer(Okio.source(resource.asByteSource().openStream()))
  }

  /**
   * Return the contents of `path` as a string, or null if no such resource exists. Note that this
   * method decodes the resource on every use. It is the caller's responsibility to cache the
   * result if it is to be loaded frequently.
   */
  fun utf8(path: String): String? {
    val source = open(path) ?: return null
    return source.use { it.readUtf8() }
  }
}
