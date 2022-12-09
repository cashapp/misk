package misk.jdbc

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.database.StartDatabaseService
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest(startService = false)
internal class MySQLSchemaMigratorServiceTest : SchemaMigratorServiceTest(DataSourceType.MYSQL)

@Disabled // TODO fix test flake, while not working in Github Actions CI, this does work locally
@MiskTest(startService = false)
internal class PostgreSQLSchemaMigratorServiceTest : SchemaMigratorServiceTest(DataSourceType.POSTGRESQL)

@MiskTest(startService = false)
internal class CockroachdbSchemaMigratorServiceTest : SchemaMigratorServiceTest(DataSourceType.COCKROACHDB)

@MiskTest(startService = false)
internal class TidbSchemaMigratorServiceTest : SchemaMigratorServiceTest(DataSourceType.TIDB)

internal abstract class SchemaMigratorServiceTest(val type: DataSourceType) {
  val deploymentModule = DeploymentModule(TESTING)

  val appConfig = MiskConfig.load<RootConfig>("test_schemamigrator_app", TESTING)
  val config = selectDataSourceConfig(appConfig)

  @MiskTestModule
  val module = Modules.combine(
    deploymentModule,
    MiskTestingServiceModule(),
    JdbcModule(Movies::class, config),
    JdbcModule(Movies2::class, config),
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

  @Inject @Movies lateinit var dataSourceService: DataSourceService
  @Inject @Movies2 lateinit var dataSourceService2: DataSourceService
  @Inject @Movies lateinit var schemaMigratorService: SchemaMigratorService
  @Inject @Movies2 lateinit var schemaMigratorService2: SchemaMigratorService
  @Inject @Movies lateinit var startDatabaseService: StartDatabaseService
  @Inject @Movies2 lateinit var startDatabaseService2: StartDatabaseService

  @AfterEach
  internal fun tearDown() {
    if (dataSourceService.isRunning) {
      dropTables()
      dataSourceService.stopAsync()
      dataSourceService2.stopAsync()
      dataSourceService.awaitTerminated()
      dataSourceService2.awaitTerminated()
    }
    if (startDatabaseService.isRunning) {
      startDatabaseService.stopAsync()
      startDatabaseService2.stopAsync()
      startDatabaseService.awaitTerminated()
      startDatabaseService2.awaitTerminated()
    }
  }

  @BeforeEach internal fun setUp() {
    startDatabaseService.startAsync()
    startDatabaseService2.startAsync()
    startDatabaseService.awaitRunning()
    startDatabaseService2.awaitRunning()
    dataSourceService.startAsync()
    dataSourceService2.startAsync()
    dataSourceService.awaitRunning()
    dataSourceService2.awaitRunning()

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

  @Test fun initializeOnMultipleTables() {
    schemaMigratorService.startAsync()
    schemaMigratorService2.startAsync()
    schemaMigratorService.awaitRunning()
    schemaMigratorService2.awaitRunning()
  }

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val cockroachdb_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig
  ) : Config
}
