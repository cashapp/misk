package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.environment.Environment
import misk.inject.toKey
import misk.jdbc.DataSourceType
import javax.inject.Singleton

/**
 * This is a test only service that migrates a test database. It should not be depended on by
 * real services. Instead real services should depend on [SchemaMigrationCheckService] which
 * this test service also produces.
 */
@Singleton
class SchemaMigratorService internal constructor(
  qualifier: kotlin.reflect.KClass<out kotlin.Annotation>,
  private val environment: misk.environment.Environment,
  private val schemaMigratorProvider: javax.inject.Provider<misk.hibernate.SchemaMigrator>, // Lazy!
  private val config: misk.jdbc.DataSourceConfig
) : AbstractIdleService(), DependentService {

  override val consumedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>(
      // Produce the SchemaMigrationCheckService since real consumers depend on it.
      SchemaMigrationCheckService::class.toKey(qualifier))

  override fun startUp() {
    require(config.type != DataSourceType.VITESS) { "Vitess should not bind SchemaMigratorService" }
    require(environment == Environment.TESTING || environment == Environment.DEVELOPMENT) {
      "SchemaMigratorService is a testing service"
    }
    val schemaMigrator = schemaMigratorProvider.get()
    val appliedMigrations = schemaMigrator.initialize()
    schemaMigrator.applyAll("SchemaMigratorService", appliedMigrations)
  }

  override fun shutDown() {
  }
}
