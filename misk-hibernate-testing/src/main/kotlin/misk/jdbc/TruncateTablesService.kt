package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.hibernate.SchemaMigratorService
import misk.hibernate.Transacter
import misk.hibernate.shards
import misk.hibernate.transaction
import misk.inject.toKey
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
internal class TruncateTablesService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val transacterProvider: Provider<Transacter>,
  private val checks: VitessScaleSafetyChecks,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf()
) : AbstractIdleService(), DependentService {
  private val persistentTables = setOf("schema_version")

  override val consumedKeys = setOf<Key<*>>(SchemaMigratorService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>()

  override fun startUp() {
    checks.disable {
      truncateUserTables()
      executeStatements(startUpStatements, "startup")
    }
  }

  override fun shutDown() {
    checks.disable {
      executeStatements(shutDownStatements, "shutdown")
    }
  }

  private fun truncateUserTables() {
    val stopwatch = Stopwatch.createStarted()

    val truncatedTableNames = transacterProvider.get().shards().flatMap { shard ->
      transacterProvider.get().transaction(shard) { session ->
        val tableNamesQuery = when (config.type) {
          DataSourceType.MYSQL -> {
            "SELECT table_name FROM information_schema.tables where table_schema='${config.database}'"
          }
          DataSourceType.HSQLDB -> {
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_TYPE='TABLE'"
          }
          DataSourceType.VITESS -> {
            "SHOW VSCHEMA_TABLES"
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

        return@transaction truncatedTableNames
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
      it.useConnection { connection ->
        for (s in statements) {
          connection.createStatement().use { statement ->
            statement.execute(s)
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
