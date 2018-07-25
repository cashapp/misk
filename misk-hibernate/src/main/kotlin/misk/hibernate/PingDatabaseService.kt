package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import com.zaxxer.hikari.util.DriverDataSource
import misk.DependentService
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.environment.Environment
import misk.inject.toKey
import java.time.Duration
import java.util.*
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Service that waits for the database to become healthy. This is needed if we're booting up a
 * Vitess cluster as part of the test run.
 */
class PingDatabaseService @Inject constructor(
  qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val environment: Environment
) : AbstractIdleService(), DependentService {

  override val consumedKeys: Set<Key<*>> = setOf()
  override val producedKeys: Set<Key<*>> = setOf(PingDatabaseService::class.toKey(qualifier))

  override fun startUp() {
    val jdbcUrl = config.type.buildJdbcUrl(config, environment)
    val dataSource = DriverDataSource(
        jdbcUrl, config.type.driverClassName, Properties(), config.username, config.password)
    retry(10, ExponentialBackoff(Duration.ofMillis(20), Duration.ofMillis(1000))) {
      dataSource.connection.use { c ->
        val result =
            c.createStatement().executeQuery("SELECT 1 FROM dual").uniqueResult { it.getInt(1) }
        check(result == 1)
      }
    }
  }

  override fun shutDown() {
  }
}
