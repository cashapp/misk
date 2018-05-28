package misk.resources

import com.google.common.reflect.ClassPath
import okio.BufferedSource
import okio.Okio
import java.util.TreeMap

/** Real implementation of [ResourceLoader] suitable for development and production. */
internal object ClasspathResourceLoader : ResourceLoader() {
  private val resourcesByPath: Map<String, ClassPath.ResourceInfo>

  init {
    val classLoader = ResourceLoader::class.java.classLoader
    val classPath = ClassPath.from(classLoader)
    resourcesByPath = TreeMap(classPath.resources.associateBy { it.resourceName })
  }

  override fun all() = resourcesByPath.keys

  override fun open(path: String): BufferedSource? {
    val resource = resourcesByPath[path] ?: return null
    return Okio.buffer(Okio.source(resource.asByteSource().openStream()))
  }

  override fun utf8(path: String): String? {
    val source = open(path) ?: return null
    return source.use { it.readUtf8() }
  }
}
