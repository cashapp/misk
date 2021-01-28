package misk.aws.dynamodb.testing

import java.io.File

internal object Libsqlite4JavaLibraryPathInitializer {
  private var initialized = false

  fun init() {
    if (initialized) return
    initialized = true

    val libraryFile = libsqlite4javaNativeLibrary()
    System.setProperty("sqlite4java.library.path", libraryFile.parent)
  }

  private fun libsqlite4javaNativeLibrary(): File {
    val prefix = libsqlite4javaPrefix()
    val classpath = System.getProperty("java.class.path")
    val classpathElements = classpath.split(File.pathSeparator)
    for (element in classpathElements) {
      val file = File(element)
      if (file.name.startsWith(prefix)) {
        return file
      }
    }
    throw IllegalArgumentException("couldn't find native library for $prefix")
  }

  /**
   * Returns the prefix of the sqlite4java native library for the current platform.
   *
   * Observed values of os.arch include:
   *  * x86_64
   *  * amd64
   *
   * Observed values of os.name include:
   *  * Linux
   *  * Mac OS X
   *
   * Available native versions of sqlite4java are:
   *  * libsqlite4java-linux-amd64-1.0.392.so
   *  * libsqlite4java-linux-i386-1.0.392.so
   *  * libsqlite4java-osx-1.0.392.dylib
   *  * sqlite4java-win32-x64-1.0.392.dll
   *  * sqlite4java-win32-x86-1.0.392.dll
   */
  private fun libsqlite4javaPrefix(): String {
    val osArch = System.getProperty("os.arch")
    val osName = System.getProperty("os.name")

    return when {
      osName == "Linux" && osArch == "amd64" -> "libsqlite4java-linux-amd64-"
      osName == "Mac OS X" && osArch == "x86_64" -> "libsqlite4java-osx-"
      else -> throw IllegalStateException("unexpected platform: os.name=$osName os.arch=$osArch")
    }
  }
}
