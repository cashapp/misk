package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.inject.Provider
import java.sql.Connection
import java.util.Locale
import kotlin.reflect.KClass
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.logging.getLogger
import misk.testing.TestFixture
import misk.vitess.shards
import misk.vitess.target
import misk.vitess.testing.DefaultSettings
import misk.vitess.testing.VitessTestDb

class JdbcTestFixture(
  private val qualifier: KClass<out Annotation>,
  private val dataSourceService: DataSourceService,
  private val transacterProvider: Provider<Transacter>,
) : TestFixture {
  private val persistentTables = setOf("schema_version")

  override fun reset() {
    if (!dataSourceService.isRunning) {
      logger.info { "Skipping truncate tables because data source is not running" }
      return
    }
    val stopwatch = Stopwatch.createStarted()

    if (dataSourceService.config().type == DataSourceType.VITESS_MYSQL) {
      val vtgatePort = dataSourceService.config().port ?: DefaultSettings.PORT
      VitessTestDb(port = vtgatePort).truncate(vtgatePort)
      logger.info("@${qualifier.simpleName} tables truncated via VitessTestDb in $stopwatch")
      return
    }

    val retryConfig = RetryConfig.Builder(3, FlatBackoff())
    val truncatedTableNames =
      retry(retryConfig.build()) {
        shards(dataSourceService).get().flatMap { shard ->
          transacterProvider.get().transaction { connection ->
            CheckDisabler.withoutChecks {
              connection.target(shard) {
                val config = dataSourceService.config()
                val nonEmptyTableNames = findNonEmptyTables(connection, config)

                val truncatedTableNames = mutableListOf<String>()
                if (nonEmptyTableNames.isNotEmpty()) {
                  connection.createStatement().use { statement ->
                    if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.TIDB) {
                      statement.execute("SET FOREIGN_KEY_CHECKS = 0")
                    }
                    try {
                      for (tableName in nonEmptyTableNames) {
                        if (config.type == DataSourceType.COCKROACHDB || config.type == DataSourceType.POSTGRESQL) {
                          statement.addBatch("TRUNCATE \"$tableName\" CASCADE")
                        } else if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.TIDB) {
                          statement.addBatch("TRUNCATE TABLE `$tableName`")
                        } else {
                          statement.addBatch("DELETE FROM `$tableName`")
                        }
                        truncatedTableNames += tableName
                      }
                      statement.executeBatch()
                    } finally {
                      if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.TIDB) {
                        statement.execute("SET FOREIGN_KEY_CHECKS = 1")
                      }
                    }
                  }
                }

                truncatedTableNames
              }
            }
          }
        }
      }

    if (truncatedTableNames.isNotEmpty()) {
      logger.info {
        "@${qualifier.simpleName} TruncateTables truncated ${truncatedTableNames.size} " + "tables in $stopwatch"
      }
    }
  }

  /**
   * Returns the names of non-empty, non-persistent tables. For MySQL and TiDB, InnoDB's
   * `information_schema.tables.table_rows` is used as a fast estimate to skip empty tables.
   * The estimate is always 0 for truly empty tables, so this is safe.
   */
  private fun findNonEmptyTables(connection: Connection, config: DataSourceConfig): List<String> {
    val tableNamesQuery =
      when (config.type) {
        DataSourceType.MYSQL,
        DataSourceType.TIDB -> {
          "SELECT table_name FROM information_schema.tables WHERE table_schema='${config.database}' AND table_type='BASE TABLE' AND table_rows > 0"
        }

        DataSourceType.HSQLDB -> {
          "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_TYPE='TABLE'"
        }

        DataSourceType.COCKROACHDB,
        DataSourceType.POSTGRESQL -> {
          "SELECT table_name FROM information_schema.tables WHERE table_catalog='${config.database}' AND table_schema='public'"
        }

        else -> {
          throw IllegalArgumentException("Unsupported database type for table truncation: ${config.type}")
        }
      }

    @Suppress("UNCHECKED_CAST")
    val allTableNames =
      connection.createStatement().use { s ->
        s.executeQuery(tableNamesQuery).map { rs -> rs.getString(1) }
      }

    return allTableNames.filter { tableName ->
      !persistentTables.contains(tableName.lowercase(Locale.ROOT)) && tableName != "dual"
    }
  }

  companion object {
    private val logger = getLogger<JdbcTestFixture>()
  }
}
