package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.deployment.Deployment
import javax.inject.Provider
import kotlin.reflect.KClass

abstract class AbstractSchemaMigratorService internal constructor(
  private val qualifier: KClass<out Annotation>,
  private val deployment: Deployment,
  private val schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  private val connectorProvider: Provider<DataSourceConnector>,
  private val runInAllDeployments: Boolean
) : AbstractIdleService(), HealthCheck, DatabaseReadyService {
  private lateinit var migrationState: MigrationState

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    val connector = connectorProvider.get()
    if (runInAllDeployments || (deployment.isTest || deployment.isLocalDevelopment)) {
      val type = connector.config().type
      if (type != DataSourceType.VITESS_MYSQL) {
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
      "SchemaMigratorService: ${qualifier.simpleName} is migrated: $migrationState"
    )
  }
}

class SchemaMigratorService internal constructor(
  qualifier: KClass<out Annotation>,
  deployment: Deployment,
  schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  connectorProvider: Provider<DataSourceConnector>,
) : AbstractSchemaMigratorService(qualifier, deployment, schemaMigratorProvider, connectorProvider, false)

class AlwaysRunSchemaMigratorService internal constructor(
  qualifier: KClass<out Annotation>,
  deployment: Deployment,
  schemaMigratorProvider: Provider<SchemaMigrator>, // Lazy!
  connectorProvider: Provider<DataSourceConnector>,
) : AbstractSchemaMigratorService(qualifier, deployment, schemaMigratorProvider, connectorProvider, true)
