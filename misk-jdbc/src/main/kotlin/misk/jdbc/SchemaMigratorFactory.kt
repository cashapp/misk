package misk.jdbc

import kotlin.reflect.KClass
import misk.resources.ResourceLoader

/**
 * Creates a [SchemaMigrator] without Guice. Useful for standalone tools like the
 * schema-migrator Gradle plugin.
 */
fun createSchemaMigrator(
  qualifier: KClass<out Annotation>,
  config: DataSourceConfig,
  dataSourceService: DataSourceService,
  resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
): SchemaMigrator {
  return when (config.migrations_format) {
    MigrationsFormat.TRADITIONAL ->
      TraditionalSchemaMigrator(
        qualifier = qualifier,
        resourceLoader = resourceLoader,
        dataSourceConfig = config,
        dataSourceService = dataSourceService,
        connector = dataSourceService,
      )
    MigrationsFormat.DECLARATIVE ->
      DeclarativeSchemaMigrator(
        resourceLoader = resourceLoader,
        dataSourceService = dataSourceService,
        connector = dataSourceService,
        skeemaWrapper = SkeemaWrapper(
          qualifier = qualifier,
          resourceLoader = resourceLoader,
          dataSourceConfig = config,
        ),
      )
    MigrationsFormat.EXTERNALLY_MANAGED ->
      throw IllegalStateException("SchemaMigrator should not be created for externally managed migrations")
  }
}
