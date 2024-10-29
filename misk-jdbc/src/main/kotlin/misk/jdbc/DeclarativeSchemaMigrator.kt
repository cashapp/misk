package misk.jdbc

import misk.resources.ResourceLoader
import wisp.logging.getLogger
import java.util.regex.Pattern
import kotlin.reflect.KClass

internal class DeclarativeSchemaMigrator(
  private val qualifier: KClass<out Annotation>,
  private val resourceLoader: ResourceLoader,
  private val dataSourceConfig: DataSourceConfig,
  private val dataSourceService: DataSourceService,
  private val connector: DataSourceConnector,
  private val skeemaWrapper: SkeemaWrapper,
) : BaseSchemaMigrator(resourceLoader, dataSourceService, connector) {

  override fun validateMigrationFile(migrationFile: MigrationFile): Boolean {
    return !Pattern.compile(connector.config().migrations_resources_regex).matcher(migrationFile.filename).matches()
  }
  override fun applyAll(author: String): MigrationStatus {
    var appliedMigrations = false
    for (shard in shards.get()) {
      val migrationFiles = getMigrationFiles(shard.keyspace)
      if (migrationFiles.isNotEmpty()) {
        skeemaWrapper.applyMigrations(migrationFiles)
        appliedMigrations = true
      }
    }
    return if (appliedMigrations) MigrationStatus.Success else MigrationStatus.Empty
  }

  override fun requireAll(): MigrationStatus {
    throw UnsupportedOperationException("not implemented")
  }
}
