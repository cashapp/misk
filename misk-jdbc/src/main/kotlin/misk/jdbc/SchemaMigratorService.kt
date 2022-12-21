package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.deployment.Deployment
import java.time.Duration
import javax.inject.Provider
import kotlin.reflect.KClass

class SchemaMigratorService internal constructor(
  private val qualifier: KClass<out Annotation>,
  private val deployment: Deployment,
  private val schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  private val connectorProvider: Provider<DataSourceConnector>
) : AbstractIdleService(), HealthCheck, DatabaseReadyService {
  private lateinit var migrationState: MigrationState

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    val connector = connectorProvider.get()
    if (deployment.isTest || deployment.isLocalDevelopment) {
      val type = connector.config().type
      if (type != DataSourceType.VITESS_MYSQL) {
        // Retry wrapped to handle multiple JDBC modules racing to create the `schema_version` table.
        retry(
          10,
          ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(5))
        ) {
          val appliedMigrations = schemaMigrator.initialize()
          migrationState = schemaMigrator.applyAll("SchemaMigratorService", appliedMigrations)
        }
      } else {
        // vttestserver automatically applies migrations
        migrationState = MigrationState(emptyMap())
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
      "SchemaMigratorService: ${qualifier.simpleName} is migrated: $migrationState"
    )
  }
}
