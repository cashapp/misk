package misk.jdbc

import com.google.common.collect.ImmutableList
import misk.resources.ResourceLoader

internal data class MigrationFile(val filename: String, val resource: String)

internal abstract class BaseSchemaMigrator(
  private val resourceLoader: ResourceLoader,
  private val connector: DataSourceConnector,
) : SchemaMigrator {

  protected abstract fun validateMigrationFile(migrationFile: MigrationFile): Boolean

  /* Reads and validates migration files from the configured resources */
  protected fun getMigrationFiles(): List<MigrationFile> {
    val migrationResources = getMigrationsResources()
    val migrationFilesForShard = mutableListOf<MigrationFile>()

    for (migrationResource in migrationResources) {
      val migrationFiles =
        resourceLoader
          .walk(migrationResource)
          .filter { it.endsWith(".sql") }
          .filter { resource ->
            connector.config().migrations_resources_exclusion?.none { excludedResource ->
              resource.contains(excludedResource)
            } ?: true
          }
          .map { MigrationFile(it, migrationResource) }
      migrationFiles.forEach {
        if (!validateMigrationFile(it)) {
          throw IllegalArgumentException("unexpected resource: ${it.filename}")
        }
      }
      migrationFilesForShard.addAll(migrationFiles)
    }
    return migrationFilesForShard.toList()
  }

  protected fun getMigrationsResources(): List<String> {
    val config = connector.config()
    val migrationsResources = ImmutableList.builder<String>()
    if (config.migrations_resource != null) {
      migrationsResources.add(config.migrations_resource)
    }
    if (config.migrations_resources != null) {
      migrationsResources.addAll(config.migrations_resources)
    }

    return migrationsResources.build()
  }
}
