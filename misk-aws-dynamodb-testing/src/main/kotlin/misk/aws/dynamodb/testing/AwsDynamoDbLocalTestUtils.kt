package misk.aws.dynamodb.testing

import java.io.File
import java.util.ArrayList
import java.util.Locale

/**
 * Helper class for initializing AWS DynamoDB to run with sqlite4java for local testing.
 *
 * Copied from: https://github.com/redskap/aws-dynamodb-java-example-local-testing
 */
object AwsDynamoDbLocalTestUtils {
  private const val BASE_LIBRARY_NAME = "sqlite4java"
  /**
   * Sets the sqlite4java library path system parameter if it is not set already.
   *
   * @param libPath Lib path for sqlite4java.
   */
  /**
   * Sets the sqlite4java library path system parameter if it is not set already.
   */
  fun initSqLite(
    libPath: String? = defaultSqliteLibPath()
  ) {
    if (System.getProperty("sqlite4java.library.path") == null) {
      System.setProperty("sqlite4java.library.path", libPath)
    }
  }

  private fun defaultSqliteLibPath(): String {
    val classPath = getClassPathList(
        System.getProperty("java.class.path"), File.pathSeparator)
    return getLibPath(System.getProperty("os.name"),
            System.getProperty("java.runtime.name"),
            System.getProperty("os.arch"), classPath)
  }

  /**
   * Calculates the possible Library Names for finding the proper sqlite4j native library and returns the directory with the most specific matching library.
   *
   * @param osName The value of `"os.name"` system property (`System.getProperty("os.name")`).
   * @param runtimeName The value of `"java.runtime.name"` system property (`System.getProperty("java.runtime.name")`).
   * @param osArch The value of `"os.arch"` system property (`System.getProperty("os.arch")`).
   * @param osArch The classpath split into strings by path separator. Value of `"java.class.path"` system property
   * (`System.getProperty("os.arch")`) split by `File.pathSeparator`.
   * @return
   */
  fun getLibPath(
    osName: String?,
    runtimeName: String?,
    osArch: String?,
    classPath: List<String>
  ): String {
    val os = getOs(osName, runtimeName)
    val libNames =
        getLibNames(os, getArch(os, osArch))
    for (libName in libNames) {
      for (classPathLib in classPath) {
        if (classPathLib.contains(libName)) {
          return File(classPathLib).parent
        }
      }
    }
    throw IllegalStateException("SQLite library \"$libNames\" is missing from classpath")
  }

  /**
   * Calculates the possible Library Names for finding the proper sqlite4java native library.
   *
   * Based on the internal calculation of the sqlite4java wrapper [Internal
 * class](https://bitbucket
    .org/almworks/sqlite4java/src/fa4bb0fe7319a5f1afe008284146ac83e027de60/java/com/almworks/sqlite4java/Internal
    .java?at=master&fileviewer=file-view-default#Internal.java-160).
   *
   * @param os Operating System Name used by sqlite4java to get native library.
   * @param arch Operating System Architecture used by sqlite4java to get native library.
   * @return Possible Library Names used by sqlite4java to get native library.
   */
  fun getLibNames(os: String, arch: String): List<String> {
    val result: MutableList<String> = ArrayList()
    val base = "$BASE_LIBRARY_NAME-$os"
    result.add("$base-$arch")
    if (arch == "x86_64" || arch == "x64") {
      result.add("$base-amd64")
    } else if (arch == "x86") {
      result.add("$base-i386")
    } else if (arch == "i386") {
      result.add("$base-x86")
    } else if (arch.startsWith("arm") && arch.length > 3) {
      if (arch.length > 5 && arch.startsWith("armv") && Character.isDigit(
              arch[4])) {
        result.add(base + "-" + arch.substring(0, 5))
      }
      result.add("$base-arm")
    }
    result.add(base)
    result.add(BASE_LIBRARY_NAME)
    return result
  }

  /**
   * Calculates the Operating System Architecture for finding the proper sqlite4java native library.
   *
   * Based on the internal calculation of the sqlite4java wrapper [Internal
 * class](https://bitbucket
    .org/almworks/sqlite4java/src/fa4bb0fe7319a5f1afe008284146ac83e027de60/java/com/almworks/sqlite4java/Internal
    .java?at=master&fileviewer=file-view-default#Internal.java-204).
   *
   * @param osArch The value of `"os.arch"` system property (`System.getProperty("os.arch")`).
   * @param os Operating System Name used by sqlite4java to get native library.
   * @return Operating System Architecture used by sqlite4java to get native library.
   */
  fun getArch(os: String, osArch: String?): String {
    var result: String
    if (osArch == null) {
      result = "x86"
    } else {
      val loweCaseOsArch = osArch.toLowerCase(Locale.US)
      result = loweCaseOsArch
      if ("win32" == os && "amd64" == loweCaseOsArch) {
        result = "x64"
      }
    }
    return result
  }

  /**
   * Calculates the Operating System Name for finding the proper sqlite4java native library.
   *
   * Based on the internal calculation of the sqlite4java wrapper [Internal
 * class](https://bitbucket
    .org/almworks/sqlite4java/src/fa4bb0fe7319a5f1afe008284146ac83e027de60/java/com/almworks/sqlite4java/Internal
    .java?at=master&fileviewer=file-view-default#Internal.java-219).*
   *
   * @param osName The value of `"os.name"` system property (`System.getProperty("os.name")`).
   * @param runtimeName The value of `"java.runtime.name"` system property (`System.getProperty("java.runtime.name")`).
   * @return Operating System Name used by sqlite4java to get native library.
   */
  fun getOs(osName: String?, runtimeName: String?): String {
    val result: String
    result = if (osName == null) {
      "linux"
    } else {
      val loweCaseOsName = osName.toLowerCase(Locale.US)
      if (loweCaseOsName.startsWith("mac") || loweCaseOsName.startsWith(
              "darwin") || loweCaseOsName.startsWith("os x")) {
        "osx"
      } else if (loweCaseOsName.startsWith("windows")) {
        "win32"
      } else {
        if (runtimeName != null && runtimeName.toLowerCase(Locale.US).contains(
                "android")) {
          "android"
        } else {
          "linux"
        }
      }
    }
    return result
  }

  /**
   * Splits classpath string by path separator value.
   *
   * @param classPath Value of `"java.class.path"` system property (`System.getProperty("os.arch")`).
   * @param pathSeparator Value of path separator (`File.pathSeparator`).
   * @return The list of each classpath elements.
   */
  private fun getClassPathList(
    classPath: String,
    pathSeparator: String
  ): List<String> {
    return classPath.split(pathSeparator)
  }
}
