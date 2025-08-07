package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provider
import misk.logging.getLogger
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
class TruncateTablesService @JvmOverloads constructor(
  private val qualifier: KClass<out Annotation>,
  private val dataSourceService: DataSourceService,
  private val transacterProvider: Provider<Transacter>,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf(),
  private val jdbcTestFixture: JdbcTestFixture = JdbcTestFixture(qualifier, dataSourceService, transacterProvider),
) : AbstractIdleService() {

  override fun startUp() {
    jdbcTestFixture.reset()
    executeStatements(startUpStatements, "startup")
  }

  override fun shutDown() {
    executeStatements(shutDownStatements, "shutdown")
  }

  private fun executeStatements(statements: List<String>, name: String) {
    val stopwatch = Stopwatch.createStarted()

    transacterProvider.get().transaction { connection ->
      CheckDisabler.withoutChecks {
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
