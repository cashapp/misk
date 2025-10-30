package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDbException
import java.io.File
import java.nio.file.Files

/**
 * This class is a wrapper around the Skeema binary (https://github.com/skeema/skeema). Some logic is forked from
 * https://github.com/cashapp/misk/blob/master/misk-jdbc/src/main/kotlin/misk/jdbc/SkeemaWrapper.kt.
 */
internal class VitessSkeema(
  private val hostname: String,
  private val mysqlPort: Int,
  private val dbaUser: String,
  private val dbaUserPassword: String,
  private val debugStartup: Boolean = false
) {
  companion object {
    val SKEEMA_BINARY = "skeema"
  }

  /**
   * Generate a schema diff for the given keyspace and Vitess cluster configuration using Skeema. We may consider using
   * https://github.com/planetscale/schemadiff in the future for diffing, but Skeema suffices for now as it also offers
   * other features we need such as linting.
   */
  fun diff(keyspace: VitessKeyspace): SkeemaDiff {
    val skeemaDirectory = prepareSkeemaDirectory(keyspace)
    try {
      val processBuilder = ProcessBuilder(listOf(skeemaBinaryPath, "diff", "--allow-unsafe"))
      processBuilder.redirectErrorStream(true) // Combine stderr and stdout
      processBuilder.directory(skeemaDirectory)

      val process = processBuilder.start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()

      if (process.exitValue() == 2) {
        throw IllegalStateException("A skeema diff error occurred: $output")
      }

      if (process.exitValue() == 0) {
        return SkeemaDiff(diff = null, hasDiff = false)
      }

      return SkeemaDiff.parseDifferences(output)
    } catch (e: Exception) {
      throw VitessTestDbException(
        "Failed to execute skeema at path: `$skeemaBinaryPath`. " +
        "Working directory: `${skeemaDirectory.absolutePath}`. " +
        "Error: `${e.message}`", e
      )
    } finally {
      skeemaDirectory.deleteRecursively()
    }
  }

  private fun prepareSkeemaDirectory(keyspace: VitessKeyspace): File {
    val tempDir = Files.createTempDirectory("skeema-").toFile()
    keyspace.ddlCommands.forEach { (filename, ddl) ->
      val file = File(tempDir, filename)
      file.writeText(ddl)
    }

    /**
     * The database name is derived from the keyspace name and the number of shards. We assume that the keyspace is
     * either unsharded or has 2 shards. For the sharded case, default to the first (-80) shard. We only need one shard
     * to generate the schema diff.
     */
    val databaseName =
      if (keyspace.shards == 2) {
        "vt_${keyspace.name}_-80"
      } else {
        "vt_${keyspace.name}_0"
      }

    val skeemaFile = File(tempDir, ".skeema")
    skeemaFile.writeText(
      """
          host=${hostname}
          port=${mysqlPort}
          user=${dbaUser}
          password=${dbaUserPassword}
          schema=$databaseName
        """
        .trimIndent()
    )

    return tempDir
  }

  private fun printDebug(message: String) {
    if (debugStartup) {
      println("[VitessSkeema] $message")
    }
  }

  private fun findSkeemaBinary(): String {
    // First, try to let OS resolve it directly (works when PATH is set)
    try {
      val process = ProcessBuilder(listOf(SKEEMA_BINARY, "--version"))
        .redirectErrorStream(true)
        .start()

      if (process.waitFor() == 0) {
        printDebug("Found skeema in PATH")
        return SKEEMA_BINARY
      }
    } catch (e: Exception) {
      printDebug("Direct skeema execution failed: ${e.message}")
    }

    // Try known absolute paths
    val absolutePaths = listOf(
      "/opt/homebrew/bin/skeema",
      "/usr/local/bin/skeema"
    )
    
    for (path in absolutePaths) {
      if (File(path).exists()) {
        printDebug("Found skeema at: $path")
        try {
          val process = ProcessBuilder(listOf(path, "--version"))
            .redirectErrorStream(true)
            .start()
          if (process.waitFor() == 0) {
            return path
          }
        } catch (e: Exception) {
          printDebug("Found skeema `$path` but couldn't execute: `${e.message}`")
        }
      }
    }
    

    throw VitessTestDbException(
      message = "Cannot find skeema binary. Tried: direct execution and absolute paths: `${absolutePaths.joinToString(", ")}`}"
    )
  }

  private val skeemaBinaryPath: String by lazy {
    findSkeemaBinary()
  }
}

internal data class SkeemaDiff(val diff: String?, val hasDiff: Boolean) {
  companion object {
    fun parseDifferences(output: String): SkeemaDiff {
      val diffPattern =
        Regex("-- instance:.*?\\n(.*?)\\n\\d{4}-\\d{2}-\\d{2}.*?diff complete", RegexOption.DOT_MATCHES_ALL)
      val diffMatch = diffPattern.find(output)
      val diff = diffMatch?.groupValues?.get(1)?.lines()?.drop(1)?.joinToString("\n") ?: ""

      return SkeemaDiff(diff = diff, hasDiff = true)
    }
  }
}
