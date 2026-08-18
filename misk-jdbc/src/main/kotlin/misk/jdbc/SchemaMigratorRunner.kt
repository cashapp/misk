package misk.jdbc

import jakarta.inject.Qualifier
import java.util.Properties
import misk.resources.ResourceLoader
import wisp.deployment.TESTING

/**
 * Standalone entry point for running schema migrations without Guice.
 * Used by the schema-migrator Gradle plugin via JavaExec.
 *
 * Reads configuration from stdin as a Properties file to avoid leaking
 * sensitive values (like passwords) in process argument lists.
 */
object SchemaMigratorRunner {
  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  private annotation class SchemaMigratorDatabase

  @JvmStatic
  fun main(args: Array<String>) {
    val props = Properties()
    props.load(System.`in`)

    val config = DataSourceConfig(
      host = props.getProperty("host")?.ifBlank { null },
      port = props.getProperty("port")?.ifBlank { null }?.toIntOrNull(),
      type = DataSourceType.valueOf(props.getProperty("databaseType")),
      migrations_resource = props.getProperty("migrationsResource"),
      database = props.getProperty("database"),
      username = props.getProperty("username"),
      password = props.getProperty("password"),
      migrations_format = MigrationsFormat.valueOf(props.getProperty("migrationsFormat")),
    )

    val configWithDefaults = config.withDefaults()

    val dataSourceService = DataSourceService(
      qualifier = SchemaMigratorDatabase::class,
      baseConfig = configWithDefaults,
      deployment = TESTING,
      dataSourceDecorators = emptySet(),
      databasePool = RealDatabasePool,
    )

    dataSourceService.startAsync().awaitRunning()
    try {
      val migrator = createSchemaMigrator(
        qualifier = SchemaMigratorDatabase::class,
        config = configWithDefaults,
        dataSourceService = dataSourceService,
        resourceLoader = ResourceLoader.SYSTEM,
      )
      migrator.applyAll("SchemaMigratorPlugin")
    } finally {
      dataSourceService.stopAsync().awaitTerminated()
    }
  }
}
