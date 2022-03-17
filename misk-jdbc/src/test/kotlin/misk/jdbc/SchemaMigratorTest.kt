package misk.jdbc

import com.google.common.collect.Iterables.getOnlyElement
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.database.StartDatabaseService
import misk.environment.DeploymentModule
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.Shard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import java.sql.SQLException
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = false)
internal class MySQLSchemaMigratorTest : SchemaMigratorTest(DataSourceType.MYSQL)

@MiskTest(startService = false)
internal class PostgreSQLSchemaMigratorTest : SchemaMigratorTest(DataSourceType.POSTGRESQL)

@MiskTest(startService = false)
internal class CockroachdbSchemaMigratorTest : SchemaMigratorTest(DataSourceType.COCKROACHDB)

@MiskTest(startService = false)
internal class TidbSchemaMigratorTest : SchemaMigratorTest(DataSourceType.TIDB)

internal abstract class SchemaMigratorTest(val type: DataSourceType) {
  val deploymentModule = DeploymentModule(TESTING)

  val appConfig = MiskConfig.load<RootConfig>("test_schemamigrator_app", TESTING)
  val config = selectDataSourceConfig(appConfig)

  @MiskTestModule
  val module = Modules.combine(
    deploymentModule,
    MiskTestingServiceModule(),
    JdbcModule(Movies::class, config)
  )

  private fun selectDataSourceConfig(config: RootConfig): DataSourceConfig {
    return when (type) {
      DataSourceType.MYSQL -> config.mysql_data_source
      DataSourceType.COCKROACHDB -> config.cockroachdb_data_source
      DataSourceType.POSTGRESQL -> config.postgresql_data_source
      DataSourceType.TIDB -> config.tidb_data_source
      DataSourceType.HSQLDB -> throw RuntimeException("Not supported (yet?)")
      else -> throw java.lang.RuntimeException("unexpected data source type $type")
    }
  }

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var dataSourceService: DataSourceService
  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  @Inject @Movies lateinit var schemaMigratorService: SchemaMigratorService
  @Inject @Movies lateinit var startDatabaseService: StartDatabaseService

  @AfterEach
  internal fun tearDown() {
    if (dataSourceService.isRunning) {
      dropTables()
      dataSourceService.stopAsync()
      dataSourceService.awaitTerminated()
    }
    if (startDatabaseService.isRunning) {
      startDatabaseService.stopAsync()
      startDatabaseService.awaitTerminated()
    }
  }

  @BeforeEach internal fun setUp() {
    startDatabaseService.startAsync()
    startDatabaseService.awaitRunning()
    dataSourceService.startAsync()
    dataSourceService.awaitRunning()

    dropTables()
  }

  private fun dropTables() {
    dataSourceService.get().connection.use { connection ->
      val statement = connection.createStatement()
      statement.addBatch("DROP TABLE IF EXISTS schema_version")
      statement.addBatch("DROP TABLE IF EXISTS table_1")
      statement.addBatch("DROP TABLE IF EXISTS table_2")
      statement.addBatch("DROP TABLE IF EXISTS table_3")
      statement.addBatch("DROP TABLE IF EXISTS table_4")
      statement.addBatch("DROP TABLE IF EXISTS library_table")
      statement.addBatch("DROP TABLE IF EXISTS merged_library_table")
      statement.executeBatch()
      connection.commit()
    }
  }

  @Test fun initializeAndMigrate() {
    val mainSource = config.migrations_resources!![0]
    val librarySource = config.migrations_resources!![1]

    resourceLoader.put(
      "$mainSource/v1002__movies.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    resourceLoader.put(
      "$librarySource/name/space/v1001__actors.sql", """
        |CREATE TABLE library_table (name varchar(255))
        |""".trimMargin()
    )

    // Initially the schema_version table is absent.
    assertThat(tableExists("schema_version")).isFalse()
    assertThat(tableExists("table_1")).isFalse()
    assertThat(tableExists("table_2")).isFalse()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertThat(tableExists("library_table")).isFalse()
    assertThat(tableExists("merged_library_table")).isFalse()
    assertFailsWith<SQLException> {
      schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)
    }

    // Once we initialize, that table is present but empty.
    schemaMigrator.initialize()
    assertThat(schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)).isEmpty()
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isFalse()
    assertThat(tableExists("table_2")).isFalse()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertThat(tableExists("library_table")).isFalse()
    assertThat(tableExists("merged_library_table")).isFalse()

    // When we apply migrations, the table is present and contains the applied migrations.
    schemaMigrator.applyAll("SchemaMigratorTest", sortedSetOf())
    assertThat(schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)).containsExactly(
      NamedspacedMigration(1001),
      NamedspacedMigration(1002),
      NamedspacedMigration(1001, "name/space/")
    )
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isTrue()
    assertThat(tableExists("table_2")).isTrue()
    assertThat(tableExists("library_table")).isTrue()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertThat(tableExists("merged_library_table")).isFalse()
    schemaMigrator.requireAll()

    // When new migrations are added they can be applied.
    resourceLoader.put(
      "$mainSource/v1003__movies.sql", """
        |CREATE TABLE table_3 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/v1004__movies.sql", """
        |CREATE TABLE table_4 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/namespace/v1001__props.sql", """
        |CREATE TABLE merged_library_table (name varchar(255))
        |""".trimMargin()
    )
    schemaMigrator.applyAll(
      "SchemaMigratorTest", sortedSetOf(
      NamedspacedMigration(1001),
      NamedspacedMigration(1002),
      NamedspacedMigration(1001, "name/space/")
    )
    )
    assertThat(schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)).containsExactly(
      NamedspacedMigration(1001),
      NamedspacedMigration(1002),
      NamedspacedMigration(1003),
      NamedspacedMigration(1004),
      NamedspacedMigration(1001, "name/space/"),
      NamedspacedMigration(1001, "namespace/")
    )
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isTrue()
    assertThat(tableExists("table_2")).isTrue()
    assertThat(tableExists("table_3")).isTrue()
    assertThat(tableExists("table_4")).isTrue()
    assertThat(tableExists("library_table")).isTrue()
    assertThat(tableExists("merged_library_table")).isTrue()
    schemaMigrator.requireAll()
  }

  @Test fun requireAllWithMissingMigrations() {
    schemaMigrator.initialize()

    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1001__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "${config.migrations_resources!![1]}/v1002__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    assertThat(assertFailsWith<IllegalStateException> {
      schemaMigrator.requireAll()
    }).hasMessage(
      """
          |Movies has applied migrations:
          |  
          |Movies is missing migrations:
          |  ${config.migrations_resources!![0]}/v1001__foo.sql
          |  ${config.migrations_resources!![1]}/v1002__foo.sql""".trimMargin()
    )
  }

  @Test fun haveOneMissingOneMigration() {
    schemaMigrator.initialize()

    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1001__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )
    schemaMigrator.applyAll("SchemaMigratorTest", sortedSetOf())

    resourceLoader.put(
      "${config.migrations_resources!![1]}/v1002__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    assertThat(assertFailsWith<IllegalStateException> {
      schemaMigrator.requireAll()
    }).hasMessage(
      """
          |Movies has applied migrations:
          |  ${config.migrations_resources!![0]}/v1001__foo.sql
          |Movies is missing migrations:
          |  ${config.migrations_resources!![1]}/v1002__foo.sql""".trimMargin()
    )
  }

  @Test fun errorOnDuplicateMigrations() {
    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1001__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1001__bar.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1002__other.sql", """
        |CREATE TABLE table_3 (name varchar(255))
        |""".trimMargin()
    )

    val duplicateFailure = assertFailsWith<IllegalArgumentException> {
      schemaMigrator.requireAll()
    }

    assertThat(duplicateFailure).hasMessageContaining("Duplicate migrations found")
    assertThat(duplicateFailure).hasMessageContaining("1001")
    assertThat(duplicateFailure).hasMessageNotContaining("1002")
  }

  @Test fun healthChecks() {
    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1002__movies.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "${config.migrations_resources!![0]}/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    schemaMigratorService.startAsync()
    schemaMigratorService.awaitRunning()

    assertThat(getOnlyElement(schemaMigratorService.status().messages)).isEqualTo(
      "SchemaMigratorService: Movies is migrated: " +
        "MigrationState(shards={keyspace/0=(all 2 migrations applied)})"
    )
  }

  private fun tableExists(table: String): Boolean {
    try {
      dataSourceService.get().connection.use { connection ->
        connection.createStatement().use {
          it.execute("SELECT * FROM $table LIMIT 1")
        }
      }
      return true
    } catch (e: SQLException) {
      return false
    }
  }

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val cockroachdb_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig
  ) : Config
}

internal class NamedspacedMigrationTest {
  @Test fun resourceVersionParsing() {
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__bar.sql")).isEqualTo(
      NamedspacedMigration(100, "foo/migrations/")
    )
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__v200.sql")).isEqualTo(
      NamedspacedMigration(100, "foo/migrations/")
    )
    assertThat(namespacedMigrationOrNull("v100_foo/migrations/v200__bar.sql")).isEqualTo(
      NamedspacedMigration(200, "v100_foo/migrations/")
    )
    assertThat(namespacedMigrationOrNull("v100_foo/migrations")).isNull()
    assertThat(namespacedMigrationOrNull("v100_foo/migrations/")).isNull()
    assertThat(namespacedMigrationOrNull("v100__bar.sql")).isEqualTo(NamedspacedMigration(100))
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__bar.SQL")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/V100__bar.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/v100_.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/luv1__franklin.sql")).isNull()
  }

  private fun namespacedMigrationOrNull(resource: String): NamedspacedMigration? {
    try {
      return NamedspacedMigration.fromResourcePath(resource, "")
    } catch (expected: IllegalArgumentException) {
      return null
    }
  }
}
