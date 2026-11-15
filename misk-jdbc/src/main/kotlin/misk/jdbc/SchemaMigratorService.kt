package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Provider
import java.time.Duration
import kotlin.reflect.KClass
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.deployment.Deployment

class SchemaMigratorService
constructor(
  private val qualifier: KClass<out Annotation>,
  private val deployment: Deployment,
  private val schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  private val connectorProvider: Provider<DataSourceConnector>,
) : AbstractIdleService(), HealthCheck, DatabaseReadyService {
  private lateinit var migrationState: MigrationStatus

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    val connector = connectorProvider.get()
    val type = connector.config().type
    if (type == DataSourceType.VITESS_MYSQL) {
      // Vitess migrations are applied externally. However, we can explore validating
      // that the schema in the target environment is actually applied
      // using the declarative validation checks in schemaMigrator.requireAll().
      migrationState = MigrationStatus.Empty
      return
    }

    if (deployment.isTest || deployment.isLocalDevelopment) {
      // Retry wrapped to handle multiple JDBC modules racing to create the `schema_version` table.
      val retryConfig = RetryConfig.Builder(10, ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(5)))
      retry(retryConfig.build()) { migrationState = schemaMigrator.applyAll("SchemaMigratorService") }
    } else {
      migrationState = schemaMigrator.requireAll()
    }
  }

  override fun shutDown() {}

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
