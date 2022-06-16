package wisp.resources

import java.io.File
import java.util.jar.JarFile
import okio.BufferedSource
import okio.buffer
import okio.source

/**
 * Read-only resources that are fetched from either the deployed .jar file or the local filesystem.
 *
 * This uses the scheme `classpath:`.
 */
object ClasspathResourceLoaderBackend : ResourceLoader.Backend() {

  const val SCHEME = "classpath:"

  override fun list(path: String): List<String> {
    require(path.startsWith("/"))
    val checkPath = path.removePrefix("/").removeSuffix("/")

    val classLoader = Thread.currentThread().contextClassLoader
    val result = mutableSetOf<String>()
    for (url in classLoader.getResources(checkPath)) {
      val urlString = url.toString()
      when {
        urlString.startsWith("file:") -> {
          val file = File(url.toURI())
          result += file.list() ?: arrayOf()
        }
        urlString.startsWith("jar:file:") -> {
          val file = jarFile(urlString)
          result += jarFileChildren(file, "$checkPath/")
        }
        else -> {
          // Silently ignore unexpected URLs.
        }
      }
    }

    return result
      .filter { !it.endsWith(".class") }
      .map { "/$checkPath/$it" }
      .toList()
  }

  /**
   * Returns a string like `/tmp/foo.jar` from a URL string like
   * `jar:file:/tmp/foo.jar!/META-INF/MANIFEST.MF`. This strips the scheme prefix `jar:file:` and an
   * optional path suffix like `!/META-INF/MANIFEST.MF`.
   */
  private fun jarFile(jarFileUrl: String): File {
    var suffixStart = jarFileUrl.lastIndexOf("!")
    if (suffixStart == -1) suffixStart = jarFileUrl.length
    return File(jarFileUrl.substring("jar:file:".length, suffixStart))
  }

  /**
   * Returns the contents of a directory inside the JAR file [file].
   *
   * @param pathPrefix a string like `misk/resources/` that ends in a slash. This will return the
   *     simple names of the files and directories that are direct descendants of this path.
   */
  private fun jarFileChildren(file: File, pathPrefix: String): Set<String> {
    // If we're looking for the children of `misk/resources/`, there's a few cases to cover:
    //  * Unrelated paths like `META-INF/MANIFEST.MF`. Ignore these.
    //  * Equal paths like `misk/resources/`. Ignore these; we're only collecting children.
    //  * Child file paths like `misk/resources/child.txt`. We collect the `child.txt` substring.
    //  * Child directory paths like `misk/resources/nested/child.txt`. We collect the `nexted`
    //    substring for the child directory.
    val result = mutableSetOf<String>()
    JarFile(file).use { jarFile ->
      for (entry in jarFile.entries().asIterator()) {
        if (!entry.name.startsWith(pathPrefix) || entry.name == pathPrefix) continue

        var endIndex = entry.name.indexOf("/", pathPrefix.length)
        if (endIndex == -1) endIndex = entry.name.length

        result += entry.name.substring(pathPrefix.length, endIndex)
      }
    }
    return result
  }

  override fun open(path: String): BufferedSource? {
    val classLoader = Thread.currentThread().contextClassLoader
    val resourceAsStream = classLoader.getResourceAsStream(path.removePrefix("/")) ?: return null
    return resourceAsStream.source().buffer()
  }

  override fun exists(path: String): Boolean {
    val classLoader = Thread.currentThread().contextClassLoader
    return classLoader.getResource(path.removePrefix("/")) != null
  }
}
