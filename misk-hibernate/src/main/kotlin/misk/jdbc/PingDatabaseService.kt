package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.zaxxer.hikari.util.DriverDataSource
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.environment.Environment
import misk.hibernate.isVitess
import misk.logging.getLogger
import java.time.Duration
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<PingDatabaseService>()

/**
 * Service that waits for the database to become healthy. This is needed if we're booting up a
 * Vitess cluster as part of the test run.
 */
@Singleton
class PingDatabaseService @Inject constructor(
  private val config: DataSourceConfig,
  private val environment: Environment
) : AbstractIdleService() {
  override fun startUp() {
    val jdbcUrl = this.config.buildJdbcUrl(environment)
    val dataSource = DriverDataSource(
        jdbcUrl, config.type.driverClassName, Properties(), config.username, config.password)
    retry(10, ExponentialBackoff(Duration.ofMillis(20), Duration.ofMillis(1000))) {
      val connection = try {
        dataSource.connect()
      } catch (e: Exception) {
        logger.error(e) { "failed to get a data source connection" }
        throw RuntimeException("failed to get a data source connection $jdbcUrl", e)
      }
      try {
        connection.use { c ->
          check(c.createStatement().use { s ->
            s.executeQuery("SELECT 1 FROM dual").uniqueInt()
          } == 1)
          // During cluster start up we sometimes have an empty list of shards so lets also
          // wait until the shards are loaded (this is generally only an issue during tests)
          if (connection.isVitess()) {
            check(connection.createStatement().use { s ->
              s.executeQuery("SHOW VITESS_SHARDS").map { rs -> rs.getString(1) }
            }.isNotEmpty())
          }
        }
      } catch (e: Exception) {
        logger.error(e) { "error attempting to ping the database" }
        throw RuntimeException(e.describe(jdbcUrl), e)
      }
    }
  }

  /** Kotlin thinks getConnection() is a val but it's really a function. */
  @Suppress("UsePropertyAccessSyntax")
  private fun DriverDataSource.connect() = getConnection()

  private fun Exception.describe(jdbcUrl: String): String {
    return when {
      message?.contains("table dual not found") ?: false -> {
        "Something is wrong with your vschema and unfortunately vtcombo does not " +
            "currently have good error reporting on this. Please do an ocular inspection."
      }
      else -> "Problem pinging url $jdbcUrl"
    }
  }

  override fun shutDown() {
  }
}
