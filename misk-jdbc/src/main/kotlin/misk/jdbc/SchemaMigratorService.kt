package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.deployment.Deployment
import java.time.Duration
import com.google.inject.Provider
import misk.backoff.RetryConfig
import kotlin.reflect.KClass

class SchemaMigratorService  constructor(
  private val qualifier: KClass<out Annotation>,
  private val deployment: Deployment,
  private val schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  private val connectorProvider: Provider<DataSourceConnector>
) : AbstractIdleService(), HealthCheck, DatabaseReadyService {
  private lateinit var migrationState: MigrationStatus

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    val connector = connectorProvider.get()
    if (deployment.isTest || deployment.isLocalDevelopment) {
      val type = connector.config().type
      if (type != DataSourceType.VITESS_MYSQL) {
        // Retry wrapped to handle multiple JDBC modules racing to create the `schema_version` table.
        val retryConfig = RetryConfig.Builder(
          10,
          ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(5))
        )
        retry(retryConfig.build()) {
          migrationState = schemaMigrator.applyAll("SchemaMigratorService")
        }
      } else {
        // vttestserver automatically applies migrations
        migrationState = MigrationStatus.Empty
      }
    } else {
      migrationState = schemaMigrator.requireAll()
    }
  }

  override fun shutDown() {
  }

  override fun status(): HealthStatus {
    val state = state()
    if (state != Service.State.RUNNING) {
      return HealthStatus.unhealthy("SchemaMigratorService: ${qualifier.simpleName} is $state")
    }

    return HealthStatus.healthy(
      "SchemaMigratorService: ${qualifier.simpleName} is migrated: ${migrationState.message()}"
    )
  }
}
