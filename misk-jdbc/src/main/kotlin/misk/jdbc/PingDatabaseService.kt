package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.zaxxer.hikari.util.DriverDataSource
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import wisp.deployment.Deployment
import wisp.logging.getLogger
import java.time.Duration
import java.util.*
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
  private val deployment: Deployment
) : AbstractIdleService() {
  override fun startUp() {
    val jdbcUrl = config.buildJdbcUrl(deployment)
    val dataSource = createDataSource(jdbcUrl)

    retry(10, ExponentialBackoff(Duration.ofMillis(20), Duration.ofMillis(1000))) {
      try {
        connectToDataSource(dataSource)
      } catch (e: Exception) {
        if (config.type == DataSourceType.VITESS_MYSQL && config.database == "@master") {
          logger.warn("ping master database unsuccessful, trying to ping the replica")
          val replicaDataSource = createDataSource(config.asReplica().buildJdbcUrl(deployment))

          connectToDataSource(replicaDataSource)
        } else {
          logger.error(e) { "error attempting to ping the database" }
          throw RuntimeException(e.describe(jdbcUrl), e)
        }
      }
    }
  }

  private fun connectToDataSource(dataSource: DriverDataSource) {
    dataSource.setLoginTimeout((config.connection_timeout.toMillis() / 1000).toInt())
    dataSource.connect().use { c ->
      check(c.createStatement().use { s ->
        s.executeQuery("SELECT 1").uniqueInt()
      } == 1)
      // During cluster start up we sometimes have an empty list of shards so lets also
      // wait until the shards are loaded (this is generally only an issue during tests)
      if (config.type.isVitess) {
        check(c.createStatement().use { s ->
          s.executeQuery("SHOW VITESS_SHARDS").map { rs -> rs.getString(1) }
        }.isNotEmpty())
      }
    }
  }

  private fun createDataSource(jdbcUrl: String): DriverDataSource {
    return DriverDataSource(
            jdbcUrl, config.type.driverClassName, Properties(), config.username, config.password)
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
