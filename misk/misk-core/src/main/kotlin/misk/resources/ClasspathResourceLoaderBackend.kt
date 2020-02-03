package misk.resources

import com.google.common.reflect.ClassPath
import okio.BufferedSource
import okio.buffer
import okio.source
import java.util.TreeMap

/**
 * Read-only resources that are fetched from either the deployed .jar file or the local filesystem.
 *
 * This uses the scheme `classpath:`.
 */
internal object ClasspathResourceLoaderBackend : ResourceLoader.Backend() {
  private val resourcesByPath: Map<String, ClassPath.ResourceInfo>

  init {
    val classLoader = ClasspathResourceLoaderBackend::class.java.classLoader
    val classPath = ClassPath.from(classLoader)
    resourcesByPath = TreeMap(classPath.resources.filter {
      it !is ClassPath.ClassInfo
    }.associateBy { "/${it.resourceName}" })
  }

  override fun open(path: String): BufferedSource? {
    val resource = resourcesByPath[path] ?: return null
    return resource.asByteSource().openStream().source().buffer()
  }

  override fun exists(path: String) = resourcesByPath[path] != null

  override fun all() = resourcesByPath.keys
}