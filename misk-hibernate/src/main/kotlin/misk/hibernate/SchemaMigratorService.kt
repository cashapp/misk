package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.environment.Environment
import misk.inject.toKey
import misk.jdbc.DataSourceType
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class SchemaMigratorService internal constructor(
  qualifier: kotlin.reflect.KClass<out kotlin.Annotation>,
  private val environment: misk.environment.Environment,
  private val schemaMigratorProvider: javax.inject.Provider<misk.hibernate.SchemaMigrator>, // Lazy!
  private val config: misk.jdbc.DataSourceConfig
) : AbstractIdleService(), DependentService {

  override val consumedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>(SchemaMigratorService::class.toKey(qualifier))

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    if (environment == Environment.TESTING || environment == Environment.DEVELOPMENT) {
      if (config.type == DataSourceType.VITESS) {
        // vttestserver automatically applies migrations but we validate the file names at least
        schemaMigrator.validateAll()
      } else {
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
