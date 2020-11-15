package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import misk.environment.Environment
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import javax.inject.Provider
import kotlin.reflect.KClass

class SchemaMigratorService internal constructor(
  private val qualifier: KClass<out Annotation>,
  private val environment: Environment,
  private val schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  private val connectorProvider: Provider<DataSourceConnector>
) : AbstractIdleService(), HealthCheck, DatabaseReadyService {
  private lateinit var migrationState: MigrationState

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    val connector = connectorProvider.get()
    if (environment == Environment.TESTING || environment == Environment.DEVELOPMENT) {
      val type = connector.config().type
      if (type != DataSourceType.VITESS && type != DataSourceType.VITESS_MYSQL) {
        val appliedMigrations = schemaMigrator.initialize()
        migrationState = schemaMigrator.applyAll("SchemaMigratorService", appliedMigrations)
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
        "SchemaMigratorService: ${qualifier.simpleName} is migrated: $migrationState")
  }
}
