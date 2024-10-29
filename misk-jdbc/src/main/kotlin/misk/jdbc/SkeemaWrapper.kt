package misk.jdbc

import misk.resources.ResourceLoader
import wisp.logging.getLogger
import java.io.File
import java.nio.file.Files
import kotlin.reflect.KClass

internal class SkeemaWrapper(
  private val qualifier: KClass<out Annotation>,
  private val resourceLoader: ResourceLoader,
  private val dataSourceConfig: DataSourceConfig,
) {

  private val logger = getLogger<SkeemaWrapper>()

  private fun skeemaPush(workDir: File): String {
    val processBuilder = ProcessBuilder(listOf(
      SKEEMA_BINARY, "push", "--allow-unsafe", "--allow-auto-inc", "int unsigned, bigint unsigned, bigint",
    ))
    processBuilder.redirectErrorStream(true) // Combine stderr and stdout
    processBuilder.directory(workDir)

    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    if (process.exitValue() != 0) {
      throw IllegalStateException("Failed to run skeema: $output")
    }

    logger.info { "${qualifier.simpleName} Skeema push output: $output" }

    return output
  }

  fun applyMigrations(migrationFiles: List<MigrationFile>) {
    val skeemaDirectory = prepareSkeemaDirectory(migrationFiles)
    try {
      skeemaPush(skeemaDirectory)
    } finally {
      skeemaDirectory.deleteRecursively()
    }
  }

  private fun prepareSkeemaDirectory(migrationFiles: List<MigrationFile>): File {
    val tempDir = Files.createTempDirectory("skeema-").toFile()
    migrationFiles.forEach {
      val file = File(tempDir, File(it.filename).name)
      file.writeText(resourceLoader.utf8(it.filename)!!)
    }
    val skeemaFile = File(tempDir, ".skeema")
    skeemaFile.writeText("""
      host=${dataSourceConfig.host}
      port=${dataSourceConfig.port}
      user=${dataSourceConfig.username}
      password=${dataSourceConfig.password}
      schema=${dataSourceConfig.database}
    """.trimIndent())

    return tempDir
  }

  companion object {
    val SKEEMA_BINARY = "${System.getenv("HERMIT_BIN")}/skeema"
  }
}
