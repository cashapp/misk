package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import misk.environment.Environment
import misk.jdbc.DataSourceType
import javax.inject.Singleton

@Singleton
class SchemaMigratorService internal constructor(
  private val environment: Environment,
  private val schemaMigratorProvider: javax.inject.Provider<SchemaMigrator>, // Lazy!
  private val config: misk.jdbc.DataSourceConfig
) : AbstractIdleService() {
  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    if (environment == Environment.TESTING || environment == Environment.DEVELOPMENT) {
      if (config.type != DataSourceType.VITESS) {
        // vttestserver automatically applies migrations
        val appliedMigrations = schemaMigrator.initialize()
        schemaMigrator.applyAll("SchemaMigratorService", appliedMigrations)
      }
    } else {
      schemaMigrator.requireAll()
    }
  }

  override fun shutDown() {
  }
}
