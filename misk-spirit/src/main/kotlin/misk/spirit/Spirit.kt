package misk.spirit

import java.io.File
import java.nio.file.Files

/**
 * Wrapper around the Spirit binary (https://github.com/block/spirit) for generating schema diffs.
 * Spirit compares a live MySQL database against SQL files and generates DDL statements.
 */
class Spirit {
  companion object {
    private const val SPIRIT_BINARY = "spirit"
    private const val NO_DIFF_OUTPUT = "-- No schema differences found."
  }

  /**
   * Generate DDL statements by comparing the live database schema against SQL files.
   *
   * @param dsn Go MySQL DSN format: `user:pass@tcp(host:port)/db`
   * @param sqlFiles Map of filename to SQL content representing the desired schema
   * @return [SchemaDiff] with DDL if differences found, or null diff if schemas match
   */
  fun diff(dsn: String, sqlFiles: Map<String, String>): SchemaDiff {
    val tempDir = Files.createTempDirectory("spirit-").toFile()
    try {
      sqlFiles.forEach { (filename, content) ->
        File(tempDir, filename).writeText(content)
      }
      return diff(dsn, tempDir)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  private fun diff(dsn: String, targetDir: File): SchemaDiff {
    val processBuilder = ProcessBuilder(
      listOf(spiritBinaryPath, "diff", "--source-dsn", dsn, "--target-dir", targetDir.absolutePath)
    )
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    // Exit 2 = schema load failure
    if (process.exitValue() == 2) {
      throw IllegalStateException("Spirit diff error: $output")
    }

    // Exit 0 = success, Exit 1 = lint violations (diff is still valid)
    if (output.contains(NO_DIFF_OUTPUT)) {
      return SchemaDiff(diff = null, hasDiff = false)
    }

    // Strip comment lines (lint info), keep DDL statements
    val ddl = output.lines()
      .filter { it.isNotBlank() && !it.startsWith("-- ") }
      .joinToString("\n")

    return if (ddl.isBlank()) {
      SchemaDiff(diff = null, hasDiff = false)
    } else {
      SchemaDiff(diff = ddl, hasDiff = true)
    }
  }

  private val spiritBinaryPath: String by lazy { findSpiritBinary() }

  private fun findSpiritBinary(): String {
    // First, try to let OS resolve it directly (works when PATH is set)
    try {
      val process = ProcessBuilder(listOf(SPIRIT_BINARY, "--version"))
        .redirectErrorStream(true).start()
      if (process.waitFor() == 0) return SPIRIT_BINARY
    } catch (_: Exception) {}

    // Try known absolute paths
    val absolutePaths = listOf("/opt/homebrew/bin/spirit", "/usr/local/bin/spirit")
    for (path in absolutePaths) {
      if (File(path).exists()) {
        try {
          val process = ProcessBuilder(listOf(path, "--version"))
            .redirectErrorStream(true).start()
          if (process.waitFor() == 0) return path
        } catch (_: Exception) {}
      }
    }

    throw IllegalStateException(
      "Cannot find spirit binary. Tried: direct execution and absolute paths: ${absolutePaths.joinToString(", ")}. " +
        "Install with: brew install block/tap/spirit"
    )
  }
}

data class SchemaDiff(val diff: String?, val hasDiff: Boolean)
