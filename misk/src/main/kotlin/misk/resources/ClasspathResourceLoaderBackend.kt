package misk.resources

import com.google.common.reflect.ClassPath
import okio.BufferedSource
import okio.Okio
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
    resourcesByPath = TreeMap(classPath.resources.associateBy { "/${it.resourceName}" })
  }

  override fun open(path: String): BufferedSource? {
    val resource = resourcesByPath[path] ?: return null
    return Okio.buffer(Okio.source(resource.asByteSource().openStream()))
  }

  override fun exists(path: String) = resourcesByPath[path] != null

  override fun all() = resourcesByPath.keys
}