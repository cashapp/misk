package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.environment.Environment
import misk.inject.toKey
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class SchemaMigratorService internal constructor(
  qualifier: KClass<out Annotation>,
  private val environment: Environment,
  private val schemaMigratorProvider: Provider<SchemaMigrator> // Lazy!
) : AbstractIdleService(), DependentService {

  override val consumedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>(SchemaMigratorService::class.toKey(qualifier))

  override fun startUp() {
    val schemaMigrator = schemaMigratorProvider.get()
    if (environment == Environment.TESTING || environment == Environment.DEVELOPMENT) {
      val appliedVersions = schemaMigrator.initialize()
      schemaMigrator.applyAll("SchemaMigratorService", appliedVersions)
    } else {
      schemaMigrator.requireAll()
    }
  }

  override fun shutDown() {
  }
}
