package misk.dev

import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

/**
 * A class loader that allows for hot reloads. If changes are detected in underlying class files this class loader
 * can be discarded and a new instance created.
 */
internal class DevClassLoader(parent: ClassLoader) : ClassLoader(parent) {

  // Map from class name to last modified time
  private val classFiles: MutableMap<String, Long> = ConcurrentHashMap()
  private val classRoots: MutableSet<String> = ConcurrentSkipListSet()

  override fun loadClass(className: String, resolve: Boolean): Class<*>? {
    val existing = findLoadedClass(className)
    if (existing != null) return existing
    if (className.startsWith("misk.") || className.startsWith("wisp.") ) {
      // Needed for the exemplar in the Misk repo itself
      return super.loadClass(className, resolve)
    }
    val classPath = className.replace('.', '/') + ".class"
    val uris = parent.getResources(classPath)
    val elements = arrayListOf<URL>()
    while(uris.hasMoreElements()) {
      elements.add(uris.nextElement())
    }
    if (elements.size != 1) {
      // If we have more than one class this element is not considered hot reloadable
      // This can happen if protos are generated in both the project and a library
      return super.loadClass(className, resolve)
    }
    val uri = elements[0]
    if (uri.protocol != "file") {
      return super.loadClass(className, resolve)
    }

    val file = File(uri.toURI())
    val root = file.absolutePath.substringBeforeLast(classPath)
    synchronized(classRoots) {
      // We want to scan for all class files eagerly
      // If there is a problem on startup they might not be loaded
      // And we still want to restart if they are changed
      if (classRoots.add(root)) {
        scanForClasses(root)
      }
    }

    val data = file.readBytes()
    val ret = defineClass(className, data, 0, data.size)

    classFiles[file.absolutePath] = file.lastModified()
    return ret
  }

  private fun scanForClasses(root: String) {
    val rootDir = File(root)
    if (!rootDir.exists() || !rootDir.isDirectory) return

    rootDir.walkTopDown().forEach { file ->
      if (file.isFile && file.extension == "class") {
        classFiles[file.absolutePath] = file.lastModified()
      }
    }
  }
}
