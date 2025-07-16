package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDbStartupException
import misk.resources.ClasspathResourceLoaderBackend
import misk.resources.FilesystemLoaderBackend
import misk.resources.ResourceLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal class VitessSchemaPreparer(
  private val lintSchema: Boolean,
  private val schemaDir: String)
{
  val keyspaces: List<VitessKeyspace>
  val currentSchemaDirPath: Path

  init {
    val supportedSchemaPrefixes = listOf(ClasspathResourceLoaderBackend.SCHEME, FilesystemLoaderBackend.SCHEME)
    val usesSupportedPrefix = supportedSchemaPrefixes.any { schemaDir.startsWith(it) }
    if (!usesSupportedPrefix) {
      throw VitessTestDbStartupException(
        "Schema directory `$schemaDir` must start with one of the supported prefixes: $supportedSchemaPrefixes"
      )
    }

    val tempSchemaDir = createTempSchemaDirectory()

    currentSchemaDirPath = tempSchemaDir
    keyspaces = VitessSchemaParser(lintSchema, schemaDir, tempSchemaDir).validateAndParse()
  }

  private fun createTempSchemaDirectory(): Path {
    val tempDir = Files.createTempDirectory("schema-")

    val resourceLoader = ResourceLoader(
      mapOf(
        ClasspathResourceLoaderBackend.SCHEME to ClasspathResourceLoaderBackend,
        FilesystemLoaderBackend.SCHEME to FilesystemLoaderBackend
      )
    )

    tempDir.createDirectories()

    if (!resourceLoader.exists(schemaDir)) {
      throw VitessTestDbStartupException("Schema directory `$schemaDir` does not exist")
    }

    resourceLoader.copyTo(schemaDir, tempDir)

    return tempDir
  }
}
