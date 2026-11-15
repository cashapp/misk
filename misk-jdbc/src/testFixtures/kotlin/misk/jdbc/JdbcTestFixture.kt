package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.inject.Provider
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
                val tableNamesQuery =
                  when (config.type) {
                    DataSourceType.MYSQL,
                    DataSourceType.TIDB -> {
                      "SELECT table_name FROM information_schema.tables where table_schema='${config.database}' AND table_type='BASE TABLE'"
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

                @Suppress("UNCHECKED_CAST") // createNativeQuery returns a raw Query.
                val allTableNames =
                  connection.createStatement().use { s ->
                    s.executeQuery(tableNamesQuery).map { rs -> rs.getString(1) }
                  }

                val truncatedTableNames = mutableListOf<String>()
                connection.createStatement().use { statement ->
                  for (tableName in allTableNames) {
                    if (persistentTables.contains(tableName.lowercase(Locale.ROOT))) continue
                    if (tableName.equals("dual")) continue

                    if (config.type == DataSourceType.COCKROACHDB || config.type == DataSourceType.POSTGRESQL) {
                      statement.addBatch("TRUNCATE \"$tableName\" CASCADE")
                    } else {
                      statement.addBatch("DELETE FROM `$tableName`")
                    }
                    truncatedTableNames += tableName
                  }
                  statement.executeBatch()
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

  companion object {
    private val logger = getLogger<JdbcTestFixture>()
  }
}
