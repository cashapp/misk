package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import misk.hibernate.Transacter
import misk.hibernate.shards
import misk.hibernate.transaction
import misk.logging.getLogger
import java.util.Locale
import javax.inject.Provider
import kotlin.reflect.KClass

private val logger = getLogger<TruncateTablesService>()

/**
 * Truncate tables before running each test.
 *
 * This deletes the data in the tables but leaves the schema as-is. It also leaves the
 * `schema_version` table as is.
 *
 * We truncate _before_ tests because that way we always have a clean slate, even if a preceding
 * test wasn't able to clean up after itself.
 */
class TruncateTablesService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val transacterProvider: Provider<Transacter>,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf()
) : AbstractIdleService() {
  private val persistentTables = setOf("schema_version")

  override fun startUp() {
    truncateUserTables()
    executeStatements(startUpStatements, "startup")
  }

  override fun shutDown() {
    executeStatements(shutDownStatements, "shutdown")
  }

  private fun truncateUserTables() {
    val stopwatch = Stopwatch.createStarted()

    val truncatedTableNames = transacterProvider.get().shards().flatMap { shard ->
      transacterProvider.get().transaction(shard) { session ->
        session.withoutChecks {
          val tableNamesQuery = when (config.type) {
            DataSourceType.MYSQL -> {
              "SELECT table_name FROM information_schema.tables where table_schema='${config.database}'"
            }
            DataSourceType.HSQLDB -> {
              "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_TYPE='TABLE'"
            }
            DataSourceType.VITESS -> {
              "SHOW VSCHEMA TABLES"
            }
          }

          @Suppress("UNCHECKED_CAST") // createNativeQuery returns a raw Query.
          val allTableNames = session.useConnection { c ->
            c.createStatement().use { s ->
              s.executeQuery(tableNamesQuery).map { rs -> rs.getString(1) }
            }
          }

          val truncatedTableNames = mutableListOf<String>()
          session.useConnection { connection ->
            val statement = connection.createStatement()
            for (tableName in allTableNames) {
              if (persistentTables.contains(tableName.toLowerCase(Locale.ROOT))) continue
              if (tableName.endsWith("_seq") || tableName.equals("dual")) continue

              statement.addBatch("DELETE FROM $tableName")
              truncatedTableNames += tableName
            }
            statement.executeBatch()
          }

          truncatedTableNames
        }
      }
    }

    if (truncatedTableNames.isNotEmpty()) {
      logger.info {
        "@${qualifier.simpleName} TruncateTablesService truncated ${truncatedTableNames.size} " +
            "tables in $stopwatch"
      }
    }
  }

  private fun executeStatements(statements: List<String>, name: String) {
    val stopwatch = Stopwatch.createStarted()

    transacterProvider.get().transaction {
      it.withoutChecks {
        it.useConnection { connection ->
          for (s in statements) {
            connection.createStatement().use { statement ->
              statement.execute(s)
            }
          }
        }
      }
    }

    if (statements.isNotEmpty()) {
      logger.info {
        "@${qualifier.simpleName} TruncateTablesService ran ${statements.size} $name " +
            "statements in $stopwatch"
      }
    }
  }
}
