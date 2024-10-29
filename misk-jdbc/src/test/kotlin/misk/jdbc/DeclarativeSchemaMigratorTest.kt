package misk.jdbc

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.database.StartDatabaseService
import misk.environment.DeploymentModule
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import java.sql.SQLException
import kotlin.test.assertFailsWith

@MiskTest(startService = false)
internal class DeclarativeSchemaMigratorTest {

  val appConfig = MiskConfig.load<RootConfig>("test_declarative_schemamigrator_app", TESTING)
  val config = appConfig.mysql_data_source

  @MiskTestModule
  val deploymentModule = DeploymentModule(TESTING)

  @MiskTestModule
  val testingModule = MiskTestingServiceModule()

  @MiskTestModule
  val jdbcModule = JdbcModule(Movies::class, config)

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
  ) : Config

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var dataSourceService: DataSourceService
  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  @Inject @Movies lateinit var startDatabaseService: StartDatabaseService
  private lateinit var declarativeSchemaMigrator : DeclarativeSchemaMigrator

  @BeforeEach
  internal fun setUp() {
    declarativeSchemaMigrator = schemaMigrator as DeclarativeSchemaMigrator
    startDatabaseService.startAsync()
    startDatabaseService.awaitRunning()
    dataSourceService.startAsync()
    dataSourceService.awaitRunning()

    dropTables()
  }

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

  private fun dropTables() {
    dataSourceService.dataSource.connection.use { connection ->
      val statement = connection.createStatement()
      statement.addBatch("DROP TABLE IF EXISTS schema_version")
      statement.addBatch("DROP TABLE IF EXISTS table_1")
      statement.addBatch("DROP TABLE IF EXISTS table_2")
      statement.addBatch("DROP TABLE IF EXISTS library_table")
      statement.executeBatch()
      connection.commit()
    }
  }

  private fun tableExists(table: String): Boolean {
    try {
      dataSourceService.dataSource.connection.use { connection ->
        connection.createStatement().use {
          it.execute("SELECT * FROM $table LIMIT 1")
        }
      }
      return true
    } catch (e: SQLException) {
      return false
    }
  }

  @Test
  fun doesNothingWhenNoMigrations() {
    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Empty)
  }

  @Test
  fun appliesMigrations() {
    val mainSource = config.migrations_resources!![0]
    val librarySource = config.migrations_resources!![1]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        |CREATE TABLE table_1 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/t2.sql", """
        |CREATE TABLE table_2 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    resourceLoader.put(
      "$librarySource/name/space/t3.sql", """
        |CREATE TABLE library_table (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    assertThat(tableExists("table_1")).isFalse
    assertThat(tableExists("table_2")).isFalse
    assertThat(tableExists("library_table")).isFalse

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    assertThat(tableExists("table_1")).isTrue
    assertThat(tableExists("table_2")).isTrue
    assertThat(tableExists("library_table")).isTrue

    // Second run should be idepmpotent
    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    assertThat(tableExists("table_1")).isTrue
    assertThat(tableExists("table_2")).isTrue
    assertThat(tableExists("library_table")).isTrue
  }

  @Test
  fun failsOnInvalidMigrations() {
    val mainSource = config.migrations_resources!![0]
    resourceLoader.put(
      "$mainSource/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    assertFailsWith<IllegalArgumentException>(message = "unexpected resource: $mainSource/v1001__movies.sql") {
      declarativeSchemaMigrator.applyAll("test")
    }

    assertThat(tableExists("table_1")).isFalse
  }

  @Test
  fun skipsExcludedMigrations() {
    val mainSource = config.migrations_resources!![0]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        |CREATE TABLE table_1 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/all-migrations.sql", """
        |CREATE TABLE table_2 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    assertThat(tableExists("table_1")).isTrue
    assertThat(tableExists("table_2")).isFalse
  }
}
