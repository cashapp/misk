package misk.jdbc

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.database.StartDatabaseService
import misk.environment.DeploymentModule
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import kotlin.test.assertFailsWith

@MiskTest(startService = false)
internal class MySQLDeclarativeSchemaMigratorTest : DeclarativeSchemaMigratorTest(DataSourceType.MYSQL)

@MiskTest(startService = false)
internal class PostgreSQLDeclarativeSchemaMigratorTest : DeclarativeSchemaMigratorTest(DataSourceType.POSTGRESQL)

@MiskTest(startService = false)
internal class CockroachdbDeclarativeSchemaMigratorTest : DeclarativeSchemaMigratorTest(DataSourceType.COCKROACHDB)

@MiskTest(startService = false)
internal class TidbDeclarativeSchemaMigratorTest : DeclarativeSchemaMigratorTest(DataSourceType.TIDB)

internal abstract class DeclarativeSchemaMigratorTest(val type: DataSourceType) {

  val appConfig = MiskConfig.load<RootConfig>("test_declarative_schemamigrator_app", TESTING)
  val config = selectDataSourceConfig(appConfig)

  @MiskTestModule
  val deploymentModule = DeploymentModule(TESTING)

  @MiskTestModule
  val testingModule = MiskTestingServiceModule()

  @MiskTestModule
  val jdbcModule = JdbcModule(Movies::class, config)

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

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val cockroachdb_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig,
  ) : Config

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var dataSourceService: DataSourceService
  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  @Inject @Movies lateinit var schemaMigratorService: SchemaMigratorService
  @Inject @Movies lateinit var startDatabaseService: StartDatabaseService
  private lateinit var declarativeSchemaMigrator : DeclarativeSchemaMigrator

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

  @BeforeEach
  internal fun setUp() {
    declarativeSchemaMigrator = schemaMigrator as DeclarativeSchemaMigrator
    startDatabaseService.startAsync()
    startDatabaseService.awaitRunning()
    dataSourceService.startAsync()
    dataSourceService.awaitRunning()

    dropTables()
  }

  private fun dropTables() {
    dataSourceService.dataSource.connection.use { connection ->
      val statement = connection.createStatement()
      statement.addBatch("DROP TABLE IF EXISTS schema_version")
      statement.executeBatch()
      connection.commit()
    }
  }

  @Test
  fun verifySkeemaBinaryIsAvailable() {
    // will fail if skeema binary is not available
    declarativeSchemaMigrator.applyAll("test")
  }

  //@Test TODO enable when implemented
  fun failsOnInvalidMigrations() {
    val mainSource = config.migrations_resources!![0]
    resourceLoader.put(
      "$mainSource/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    assertFailsWith<IllegalArgumentException>(message = "unexpected resource: $mainSource/v1001__movies.sql") {
      declarativeSchemaMigrator.requireAll()
    }
  }


}
