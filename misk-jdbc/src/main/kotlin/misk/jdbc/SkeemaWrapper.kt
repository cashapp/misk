package misk.jdbc

import jakarta.inject.Singleton
import misk.resources.ResourceLoader
import java.io.File
import java.nio.file.Files
import kotlin.reflect.KClass

@Singleton
internal class SkeemaWrapper(
  private val qualifier: KClass<out Annotation>,
  private val resourceLoader: ResourceLoader,
  private val dataSourceConfig: DataSourceConfig,
) {
  private fun runBinary(): String {
    val processBuilder = ProcessBuilder(listOf(SKEEMA_BINARY))
    processBuilder.redirectErrorStream(true) // Combine stderr and stdout

    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor() // Wait for the process to finish

    if (process.exitValue() != 0) {
      throw IllegalStateException("Failed to run skeema: $output")
    }

    return output
  }

  fun runSkeema(): String {
    return runBinary()
  }

  companion object {
    val SKEEMA_BINARY = "${System.getenv("HERMIT_BIN")}/skeema"
  }
}
