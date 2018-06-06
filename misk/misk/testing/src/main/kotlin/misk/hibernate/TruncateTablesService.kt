package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.getLogger
import org.hibernate.SessionFactory
import org.hibernate.query.Query
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
  private val sessionFactoryProvider: Provider<SessionFactory>,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf()
) : AbstractIdleService(), DependentService {
  private val persistentTables = setOf("schema_version")

  override val consumedKeys = setOf<Key<*>>(SchemaMigratorService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>()

  override fun startUp() {
    truncateUserTables()
    executeStatements(startUpStatements, "startup")
  }

  override fun shutDown() {
    executeStatements(shutDownStatements, "shutdown")
  }

  private fun truncateUserTables() {
    val stopwatch = Stopwatch.createStarted()

    val truncatedTableNames = sessionFactoryProvider.get().openSession().use { session ->
      val tableNamesQuery = when (config.type) {
        DataSourceType.MYSQL -> {
          TODO()
        }
        DataSourceType.HSQLDB -> {
          "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_TYPE='TABLE'"
        }
      }

      @Suppress("UNCHECKED_CAST") // createNativeQuery returns a raw Query.
      val query = session.createNativeQuery(tableNamesQuery) as Query<String>
      val allTableNames = query.list()

      val truncatedTableNames = mutableListOf<String>()
      session.doReturningWork { connection ->
        val statement = connection.createStatement()
        for (tableName in allTableNames) {
          if (persistentTables.contains(tableName.toLowerCase(Locale.ROOT))) continue

          statement.addBatch("DELETE FROM $tableName")
          truncatedTableNames += tableName
        }
        statement.executeBatch()
      }

      return@use truncatedTableNames
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

    sessionFactoryProvider.get().openSession().doWork { connection ->
      for (s in statements) {
        connection.createStatement().use { statement ->
          statement.execute(s)
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