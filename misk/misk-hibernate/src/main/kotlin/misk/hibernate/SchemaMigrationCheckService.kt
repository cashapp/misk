package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.toKey
import javax.inject.Singleton

/**
 * Validates that all migrations have been applied
 */
@Singleton
class SchemaMigrationCheckService internal constructor(
  qualifier: kotlin.reflect.KClass<out kotlin.Annotation>,
  private val schemaMigratorProvider: javax.inject.Provider<misk.hibernate.SchemaMigrator> // Lazy!
) : DependentService, AbstractIdleService() {

  override val consumedKeys = setOf<Key<*>>(SchemaMigratorService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>()

  override fun startUp() {
    schemaMigratorProvider.get().requireAll()
  }

  override fun shutDown() {
  }
}
